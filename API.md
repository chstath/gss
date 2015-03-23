This is a description of the REST-like API for interacting with the GSS service. For library implementations in various languages see [APIClients](http://code.google.com/p/gss/wiki/APIClients).

# Entities #

The GSS service defines a few entities that can be manipulated via the GSS REST API. These entities are described below.

## Files ##

Files are the most basic resources in GSS. They represent actual operating system files from the client's computer that have been augmented with extra metadata for storage, retrieval and sharing purposes. Familiar metadata from modern file systems are also maintained in GSS, like file name, creation/modification times, creator, modifier, tags, permissions, etc. Files can also be versioned in GSS. Updating versioned files retains the previous versions, while updating an unversioned file replaces irrevocably the old file contents.


## Folders ##

Folders are resources that are used for grouping files. They represent the file system concept of folders or directories and can be used to mirror a client's computer file system on GSS. Familiar metadata from modern file systems are also maintained in GSS, like folder name, creation/modification times, creator, modifier, permissions, etc.


## Users ##

Users are the entities that represent the actual users of the system. They are used to login to the service and separate namespaces of files and folders. Users have attributes like full name, e-mail, username, authentication token, creation/modification times, groups, etc.


## Groups ##

Groups are entities used to organize users for easier sharing of files and folders among peers. They can be used to facilitate sharing files to multiple users at once.


## Permissions ##

Permissions are collections of rights users or groups have on a particular resource. The available rights are Read, Write and Modify ACL. Read allows a user to view the contents of a folder or file. Write permits modification of a file or folder. Modify ACL lets a user give or take away rights to the resource from a user or group.


# Authentication #

User authentication in GSS is performed in a distributed/federated architecture, using Shibboleth. User passwords are never stored or received by GSS. Users are authenticated in their own organization's Identity Provider and after that GSS receives a username and creates and stores its own authentication token for use throughout the GSS API.

There are two authentication methods provided for allowing users to log into GSS, one suitable mainly for web applications and another more suitable for desktop client applications. However there is no formal requirement for a client application to use one or the other. Developers can pick the one that makes more sense for their particular use case.

In the following sections we will assume a user with the name aaitest and a username of aaitest@uth.gr.

## Web clients ##

```
http://hostname/gss/
```

This is the URL of the web client. When the client discovers that no authentication cookie exists, it redirects the user to the login page.

```
https://hostname/gss/login?next=http://hostname/gss/
```

This is the only Shibboleth-protected URL. After logging in via Shibboleth, the login page creates the token & expiry date (if necessary). Afterwards it stores the username & token in a cookie:

```
    _gss_a = username|token

    For example:

    _gss_a = aaitest%40uth.gr|NZ5Pk+kdZQAuL4NOnxVTOAs/2a7wglcK5HSAIoheYmcxee601HT1sA==
```

Then it redirects the browser to the 'next' URL specified by the web client. In the future, when multiple apps are using GSS login there should be a page displayed before the redirect informing user about it and asking permission to allow the web application to receive the user's credentials.

```
http://hostname/gss/
```

The client retrieves the username and token from cookie and then immediately clears it from the cookie file. From now on communication can be authenticated via signed requests.

Note that the server cannot set a cookie for a different domain, therefore other web clients must have at least one component hosted under _hostname_.

## Desktop clients ##

```
http://hostname/gss/nonce?user=aaitest@uth.gr
```

This is the Nonce Issuer service that creates one-time tokens for retrieving the authentication token. Returns the nonce and stores it in a separate DB table, along with its expiry date:

```
    6sfgBwEeW28YVOy0tGrJ8t8Zr4g=
```

This table contains references to the associate user objects, but no foreign key constraints, so that this functionality can be separated in a dedicated node. This way DoS attacks on the nonce issuer service (which have to open to all) will have no impact on GSS, except stalling desktop client logins. Nonce time-to-live is set to 5 minutes. If the user cannot be found, the server returns HTTP code 403 (Forbidden).

```
https://hostname/gss/login?nonce=6sfgBwEeW28YVOy0tGrJ8t8Zr4g=
```

This is the only Shibboleth-protected URL. After logging in via Shibboleth, the login page creates the token & expiry date (if necessary). If the nonce is not found or is not valid, it informs the user about it without allowing further token retrieval with the supplied nonce. Otherwise it activates the nonce, replacing it if there was another one activated.

```
https://hostname/gss/token?user=aaitest@uth.gr&nonce=6sfgBwEeW28YVOy0tGrJ8t8Zr4g=
```

If a matching entry is found for the specified user and nonce values with a valid authentication token, the active nonce is removed and the authentication token is returned. If no match could be found, HTTP code 403 (Forbidden) is returned. If the token is invalid HTTP code 401 (Unauthorized) is returned.

Note that the token binary value has been base64-encoded in order to be transferred along with the response. Therefore it needs to be converted back to binary form by the client before use.

# Request signing #

REST API requests must be signed in order to avoid forgery. The only exception from this rule are requests to retrieve files that are specifically marked as open for reading by everybody. Signing is accomplished by calculating a signature and storing it along with the username in the Authorization HTTP header. The format is `"username signature"`. The signature is comprised by the concatenation of the HTTP method string, the time the request is made (formatted as specified in RFC 2616, section 14.18) and the path of the requested resource. So for the REST API request:

```
    http://hostname/gss/rest/aaitest@uth.gr/files/
```

the signature would be the concatenation of:

  * the HTTP method: "GET"
  * the time the request is being made: "Tue, 04 Nov 2008 13:22:55 GMT"
  * the path of the resource: "/aaitest@uth.gr/files/"

The path would be available from a previous API response and does not include the common for all requests REST API endpoint (http://hostname/gss/rest), nor any request parameters (anything after ? should be omitted).

Besides the Authorization header, a timestamp must be provided in order to thwart replay attacks. The server will discard requests with a timestamp that is more than 10 minutes off from the time it is received. The timestamp must be in the format specified in RFC 2616, section 14.18 and may be stored in the standard HTTP Date header or in a special purpose X-GSS-Date header. Since RFC 2616 specifies that Date headers should be supplied in PUT and POST requests and client implementations may be forbidding the setting of the Date header for security purposes, it is recommended to use X-GSS-Date for all requests. If the server receives a request with an invalid signature or if the user's authentication token has expired, it will return HTTP code 403 (Forbidden). In that case, the client should request a reauthentication for the user, in order to obtain the newly-generated token.

For the example URI mentioned above, the authorization and date headers would be:

```
    Authorization: aaitest@uth.gr UbAaWL2HzFqSrhaD/xOB4FXTL8w=

    X-GSS-Date: Tue, 04 Nov 2008 13:22:55 GMT
```

The signature was obtained by starting with the string "GETTue, 04 Nov 2008 13:22:55 GMT/aaitest@uth.gr/files/", applying an HMAC-SHA1 message digest algorithm on it, with the authentication token received during user authentication and then encoding it in Base64 for transmission. It should be noted that the authentication token received from the server is already base64-encoded. Therefore it should be decoded before being used as an input to the HMAC-SHA1 algorithm.

There is an alternative method of performing authenticated GET requests for files, especially for browser-based JavaScript applications, that would like to display links to file resources. In this scheme, the Authorization and Date headers are submitted as request parameters with the same names and values (albeit URL-encoded), like this:

```
    http://hostname/gss/rest/aaitest@uth.gr/files/doc.txt?Date=Mon,%2009%20Mar%202009%2010:53:54%20GMT&Authorization=aaitest@uth.gr%20IAM792zu0fntGH9p1s2whQ26yYQ=
```

# Interaction #

## Retrieving the user's home page ##

Each user in GSS has what amounts to a home page in traditional HTTP servers. This is a URI that serves as the entrance point to the service:

```
http://hostname/gss/rest/aaitest@uth.gr/
```

Requests to the above URI retrieve information about the user, as well as hyperlinks to the rest of the resources. A GET request to the above URI returns the following JSON message:

```
    {
        "name": "aaitest",
        "username": "aaitest@uth.gr",
        "email": "aaitest@uth.gr",
        "fileroot": "http://hostname/gss/rest/aaitest@uth.gr/files",
        "trash": "http://hostname/gss/rest/aaitest@uth.gr/trash",
        "shared": "http://hostname/gss/rest/aaitest@uth.gr/shared",
        "others": "http://hostname/gss/rest/aaitest@uth.gr/others",
        "tags": "http://hostname/gss/rest/aaitest@uth.gr/tags",
        "groups": "http://hostname/gss/rest/aaitest@uth.gr/groups",
        "creationDate": 1223372769275,
        "modificationDate": 1223372769275,
        "lastLogin": 1223372769275,
        "quota": {
            "totalFiles": 7,
            "totalBytes": 429330,
            "bytesRemaining": 10736988910
        } 
    }
```

The response to this request provides links to the available resources associated with this user, namely:

  * fileroot: provides the link to the URI namespace for manipulating files and folders that belong to the user
  * trash: provides the link to the URI namespace for manipulating the trash can virtual folder
  * shared: provides the link to the URI namespace for the virtual folder with shortcuts to the files and folders, shared by this user
  * others: provides the link to the URI namespace for files and folders shared by other users that the current user has access to
  * tags: provides the link to the URI namespace for file tags specified by the user
  * groups: provides the link to the URI namespace for the groups defined by this user


Clients should not expect these sub-namespaces to follow the format in the example above, but should use whatever hypelink is specified for each attribute. RESTful applications should not be tied to a particular URI structure for any available resource. Note that the dates (creationDate, modificationDate) specified in all objects are specified as milliseconds since January 1, 1970, 00:00:00 GMT. If the user making the request is not the same as the user whose information is being requested, HTTP status code 405 (Method Not Allowed) will be returned.

## Manipulating files and folders ##

Each user has an implicit root folder that contains all of his files and folders:

```
http://hostname/gss/rest/aaitest@uth.gr/files/
```

We need the username in the URI above for distinguishing between the namespaces of different users. Otherwise it would be impossible to provide meaningful hyperlinks to shared resources. A GET request to the above URI returns a JSON representation of the root folder:

```
    {
        "name": "aaitest",
        "owner": "aaitest@uth.gr",
        "createdBy": "aaitest@uth.gr",
        "creationDate": 1223372769317,
        "modifiedBy": "aaitest@uth.gr",
        "modificationDate": 1223372769317,
        "deleted": false,
        "shared":false,
        "readForAll":true,
        "files": [
            {
                "name": "sha1.js",
                "owner": "aaitest@uth.gr",
                "creationDate": 1233758218866,
                "modificationDate": 1233758218866,
                "deleted": false,
                "size": 1610,
                "content": "application/javascript",
                "shared":false,
                "versioned":true,
                "version": 1,
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/sha1.js",
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                    "name": "aaitest"
                },
                "path": "/"
            },
            {
                "name": "proposal",
                "owner": "aaitest@uth.gr",
                "creationDate": 1433758218866,
                "modificationDate": 1433758218866,
                "deleted": false,
                "size": 3610,
                "content": "text/plain"
                "shared":false,
                "versioned":true,
                "version": 1,
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/proposal",
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                    "name": "aaitest"
                },
                "path": "/"
            },
            {
                "name": "signature.txt",
                "owner": "aaitest@uth.gr",
                "creationDate": 1233753458866,
                "modificationDate": 1233753458866,
                "deleted":false,
                "size":230,
                "content": "text/plain",
                "shared":false,
                "versioned":true,
                "version": 1,
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/signature.txt",
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                    "name": "aaitest"
                },
                "path": "/"
            }
        ],
        "folders": [
            {
                "name": "Documents",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents"
            }
        ]
    }
```

Note that we use the full name of the user as the name of the root folder. Also note that the files and folders contained in the requested folder are present in the response with a JSON object that contains a subset of their properties. This subset contains enough information that clients should only need one API request to display a folder.

### Folders ###

```
http://hostname/gss/rest/aaitest@uth.gr/files/Documents
```

A particular folder in the user namespace. A GET request on this URI returns a JSON representation of the folder:

```
    {
        "name": "Documents",
        "owner": "aaitest@uth.gr",
        "deleted": false,
        "shared":false,
        "readForAll":true,
        "createdBy": "aaitest@uth.gr",
        "creationDate": 1223372795825,
        "modifiedBy": "aaitest@uth.gr",
        "modificationDate": 1223372795825,
        "parent": {
            "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
            "name": "aaitest"
        },
        "files": [
            {
                "name": "doc.txt",
                "owner": "aaitest@uth.gr",
                "creationDate": 1233758218866,
                "modificationDate": 1233758218866,
                "deleted":false,
                "size":4567,
                "content": "text/plain",
                "version": 1,
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/doc.txt",
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/",
                    "name": "Documents"
                },
                "path": "/Documents/"
            }
        ],
        "folders": [],
        "permissions": [
            {
                "modifyACL": true,
                "write": true,
                "read": true,
                "user": "aaitest@uth.gr"
            },
            {
                "modifyACL": false,
                "write": true,
                "read": true,
                "group": "Work"
            }
        ]
    }
```

One difference of a regular folder with the root folder (of the 'files' namespace) is that the former contains a "parent" property with an object containing the name and the URI of the folder that contains this one. The root folder having no implicit parent (essentially the parent of the root folder is itself) does not contain a parent property. The existence of this property attests to the fact that the API goes in great strides to avoid having the client parsing resource URIs.

A HEAD request on this URI will return the X-GSS-Metadata HTTP header among others. This header contains a JSON representation of folder metadata, as in files, but contrary to files, only a small subset of basic metadata is returned. The parent folder, children (files or folders) and permissions are not part of this JSON representation:

```
    X-GSS-Metadata: {
        "name": "Documents",
        "owner": "aaitest@uth.gr",
        "deleted": false,
        "shared":false,
        "readForAll":true,
        "createdBy": "aaitest@uth.gr",
        "creationDate": 1223372795825,
        "modifiedBy": "aaitest@uth.gr",
        "modificationDate": 1223372795825
    }
```

This header mainly exists to simplify clients or client libraries that operate not with a navigational paradigm, like file explorers, but with a random access paradigm, like file system implementations. Most clients should be content with the GET operation that returns the full JSON representation of the folder and ignore the additional X-GSS-Metadata header.

A POST request on this URI with a "new" parameter, would create a new subfolder inside this folder, using the name in the "new" parameter as the new folder name, returning the HTTP status code 201 (Created). For instance, if a POST request with the parameter "new=work" was made to the previous URI, a new folder named "work" would be created and it's URI would be returned in the Location header of the HTTP response, as well as the response body:

```
    Location: http://hostname/gss/rest/aaitest@uth.gr/files/Documents/work
    Content-Type: text/plain;charset=ISO-8859-1
    Content-Length: 66
    Date: Mon, 26 Jan 2009 15:36:23 GMT

    http://hostname/gss/rest/aaitest@uth.gr/files/Documents/work
```

If the user does not have the necessary permission to create the folder, HTTP status code 405 (Method Not Allowed) will be returned. If a folder with the same name exists in that level, HTTP status code 405 (Method Not Allowed) will be returned, with an additional Allow header containing the allowed methods for the existing resource. Permanently deleting a folder (as opposed to moving it to the trash) can be performed by using a DELETE request on this URI.

### Copying folders ###

Copying a folder can be done by fetching the folder and it's contents locally and then creating the folder and it's subfolders in the new location with a series of POST and PUT commands. This however is tedious, cumbersome and slow. A shortcut for copying a folder from one remote location to another is to make a POST request to the folder's URI, with the parameter "copy" having a value of the destination location URI. If, for instance, a POST is made to the above URI with parameter "copy=http://hostname/gss/rest/aaitest@uth.gr/files/Docs", the folder Documents will be copied along with it's children to the root folder, in the same level as Documents, with the new name Docs. If the user is not allowed to copy the folder, HTTP status code 405 (Method Not Allowed) will be returned. If the destination URI already exists, an HTTP status 409 (Conflict) will be returned. If the copy causes the user's quota limit to be exceeded, HTTP status 413 (Request entity too large) will be returned.

### Moving folders ###

Moving a folder can be done by fetching the folder and it's contents locally, deleting the folder on the server and then creating the folder and it's subfolders in the new location with a series of POST and PUT commands. This however is tedious, cumbersome and slow. A shortcut for moving a folder from one remote location to another is to make a POST request to the folder's URI, with the parameter "move" having a value of the destination location URI. If, for instance, a POST is made to the above URI with parameter "move=http://hostname/gss/rest/aaitest@uth.gr/files/Work/Docs", the folder Documents will be moved along with it's children to the Work subfolder of the root folder, with the new name Docs. If the user is not allowed to move the folder, HTTP status code 405 (Method Not Allowed) will be returned. If the destination URI already exists, an HTTP status 409 (Conflict) will be returned.

### Modifying folders ###

Modifying a folder is done by making a POST to the folder's URI, adding the parameter "update" with an empty value (update=). The metadata to modify must be supplied in the request body as a JSON-encoded object, like this:

```
    {
        "name": "Documents2",
        "permissions": [
            {
                "modifyACL": true,
                "write": true,
                "read": true,
                "user": "friend@domain"
            },
            {
                "modifyACL": true,
                "write": true,
                "read": true,
                "user": "aaitest@uth.gr"
            },
            {
                "modifyACL": false,
                "write": true,
                "read": true,
                "group": "Friends"
            }
        ]
    }
```

This request changes the name of the folder to "Documents2" and modifies the set of permissions on it, so that user friend@domain has full privileges and group Friends has read/write access. Note that the flags with false values can be omitted from the request, since false is the default. More importantly, note that when updating the permission list, the former one is removed and the submitted one takes it's place. No merging is being done, therefore the owner's permissions must be sent again with the new ones. Care should be taken not to render a folder inaccessible by replacing the permissions list with an incomplete one. The absence of any one of the above fields leaves the current field value intact.

When a request to change the name of a folder is made, the response body contains the new URL of the folder.

### Moving a folder to the trash ###

Moving a folder to the trash can be done by making a POST request to the folder's URI, adding the parameter "trash" with an empty value (trash=). If the user is not allowed to remove the folder, HTTP status code 405 (Method Not Allowed) will be returned. When a folder is moved to the trash, the folder's URI does not change, so further manipulation of the folder (like permanently deleting or restoring it) is still performed by accessing this URI.

### Restoring a folder from the trash ###

Restoring a folder from the trash can be done by making a POST request to the folder's URI, adding the parameter "restore" with an empty value (restore=). If the user is not allowed to restore the folder, HTTP status code 405 (Method Not Allowed) will be returned.


### Files ###

```
http://hostname/gss/rest/aaitest@uth.gr/files/Documents/doc.txt
```

A particular file in the user namespace. A GET request on this URI returns the specified file, with the proper HTTP response headers set appropriately:

```
    Etag: "5752-1224679305506"
    Last-Modified: Wed, 22 Oct 2008 15:41:45 GMT
    Content-Length: 5752
    Content-Type: text/plain
    X-GSS-Metadata: {
            "name": "doc.txt",
            "creationDate": 1232449958563,
            "createdBy": "aaitest@uth.gr",
            "readForAll": true,
            "modifiedBy": "aaitest@uth.gr",
            "owner": "aaitest@uth.gr",
            "modificationDate": 1232449958563,
            "deleted": false;
            "shared":false,
            "versioned":true,
            "readForAll":true,
            "version": 2,
            "size": 5752,
            "content": "text/plain",
            "uri": "http://hostname/gss/rest/aaitest2@uth.gr/files/Documents/doc.txt",
            "folder": {
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/",
                "name": "Documents"
            },
            "path": "/Documents/",
            "tags": [
                "work",
                "personal"
            ],
            "permissions": [
                {
                    "modifyACL": true,
                    "write": true,
                    "read": true,
                    "user": "aaitest@uth.gr"
                },
                {
                    "modifyACL": false,
                    "write": true,
                    "read": true,
                    "group": "Work"
                }
            ]
        } 
```

Here we have received the size of the file (5752 bytes), the type of the resource (plain text), the file metadata (X-GSS-Metadata), the last modification time and the (strong) Etag. One can also perform conditional requests using various HTTP headers, like If-Modified-Since, If-Unmodified-Since, If-Match, If-None-Match or make partial content requests using the Range header. A HEAD request on this URI retrieves only the HTTP headers containing the file's metadata, but not the file contents. A PUT request on this URI causes the file to be updated with the supplied data and a new version of the resource to be created, if versioning was enabled. HTTP status 204 is returned in such case. Partial puts are also supported. File creation is the same as update, i.e. using PUT. If the user is not allowed to create or update the file, HTTP status code 405 (Method Not Allowed) will be returned. If a folder with the same name exists in that level, HTTP status code 409 (Conflict) will be returned. The same status code (409) will be returned if the parent folder of the file does not exist. If uploading the file exceeds the user's quota limit, HTTP status 413 (Request entity too large) will be returned. Permanently deleting a file (as opposed to moving it to the trash) can be performed by using a DELETE request on this URI.

One important thing to be aware of is that the file name is URL-encoded when transmitted via the X-GSS-Metadata header, but not when it is transmitted in the response body, for example when performing a GET request on the parent folder. The reason is that the valid character set in an HTTP header is fairly limited, thus requiring an encoding transfer.

There is an alternative file upload mechanism supported specifically for browser-based applications. Since web browsers do not provide JavaScript applications with access to the user file system, the only way to upload a file is by using an HTML form with a multipart encoding. In order to upload a file to GSS, the form's action parameter must point to the file URI, the enctype must be multipart/form-data and two hidden fields must be sent along with the file contents, named Date and Authorization. The contents of these fields should be the same as the respective headers in all other protocol operations, namely the time of the request for Date, and the username and signature for Authorization:

```
<form name="upload" method="post" action="http://hostname/gss/rest/aaitest@uth.gr/files/doc.txt" enctype="multipart/form-data">
    <input type="hidden" name="Date" value="Tue, 04 Nov 2008 13:22:55 GMT">
    <input type="hidden" name="Authorization" value="aaitest@uth.gr UbAaWL2HzFqSrhaD/xOB4FXTL8w=">
    <input type="file" name="file">
    <input type="submit">
</form>
```

Client applications can monitor the upload progress and display a progress bar to the user, by counting the number of bytes already transmitted against the total file size. Browser-based JavaScript applications have no access to the byte stream being transferred, however. For these cases there is an API method to retrieve the number of bytes transferred, as well as the file size:

```
http://hostname/gss/rest/aaitest@uth.gr/files/Documents/doc.txt?progress=doc.txt
```

A GET request on the above URI will return the following JSON object, for a file upload in progress, in that same URI:

```
{
    "bytesUploaded": 600,
    "bytesTotal": 1000
}
```

For file updates, the value of the `progress` parameter can be omitted, since the file name can be derived from the URI. For uploads of new files however, it is mandatory, since the server is treating the URI as opaque, without assumptions about file names and paths. A finished upload will cause the progress request to return HTTP status 404 (Not found).

Note however that this method is much less accurate and much less efficient for regular clients, than relying on local data from the file stream. Therefore its use is **highly discouraged** for all clients, besides browser-based ones.

### Copying files ###

Copying a file can be done by fetching the file locally and then creating the file in the new location with a PUT command. This however is tedious, cumbersome and slow. A shortcut for copying a file from one remote location to another is to make a POST request to the file's URI, with the parameter "copy" having a value of the destination location URI. If, for instance, a POST is made to the above URI with parameter "copy=http://hostname/gss/rest/aaitest@uth.gr/files/doc.txt", the file doc.txt will be copied to the root folder, in the same level as Documents, with the same name doc.txt. If the user is not allowed to copy the file, HTTP status code 405 (Method Not Allowed) will be returned. If the destination URI already exists, an HTTP status 409 (Conflict) will be returned. If the copy causes the user's quota limit to be exceeded, HTTP status 413 (Request entity too large) will be returned.

### Moving files ###

Moving a file can be done by fetching the file locally, deleting the file on the server and then creating the file in the new location with a PUT command. This however is tedious, cumbersome and slow. A shortcut for moving a file from one remote location to another is to make a POST request to the file's URI, with the parameter "move" having a value of the destination location URI. If, for instance, a POST is made to the above URI with parameter "move=http://hostname/gss/rest/aaitest@uth.gr/files/doc.txt", the file doc.txt will be moved to the root folder, with the same name doc.txt. If the user is not allowed to move the folder, HTTP status code 405 (Method Not Allowed) will be returned. If the destination URI already exists, an HTTP status 409 (Conflict) will be returned.

### Modifying files ###

Modifying a file is done by making a POST to the file's URI, adding the parameter "update" with an empty value (update=). The metadata to modify must be supplied in the request body as a JSON-encoded object, like this:

```
    {
        "name": "doc2.txt",
        "shared":false,
        "versioned":true,
        "readForAll":true,
        "tags": [
            "work",
            "personal",
            "documents"
        ],
        "permissions": [
            {
                "modifyACL": true,
                "write": true,
                "read": true,
                "user": "friend@domain"
            },
            {
                "modifyACL": true,
                "write": true,
                "read": true,
                "user": "aaitest@uth.gr"
            },
            {
                "modifyACL": false,
                "write": true,
                "read": true,
                "group": "Friends"
            }
        ]
    } 
```

This request changes the name of the file to "doc2.txt", provides a new set of tags for the file, sets the read-for-all and versioned flags, and modifies the set of permissions on it, so that user friend@domain has full privileges and group Friends has read/write access. Note that the permission flags with false values can be omitted from the request, since false is the default, unlike read-for-all and versioned flags, whose absence denotes no desire to modify their current value. More importantly, note that when updating the permission list as well as the tag list, the former one is removed and the submitted one takes it's place. No merging is being done, therefore the owner's permissions (or the previously set tags) must be sent again with the new ones. Care should be taken not to render a file inaccessible by replacing the permissions list with an incomplete one. The absence of any one of the above fields leaves the current field value intact.


### Moving a file to the trash ###

Moving a file to the trash can be done by making a POST request to the file's URI, adding the parameter "trash" with an empty value (trash=). If the user is not allowed to remove the file, HTTP status code 405 (Method Not Allowed) will be returned. When a file is moved to the trash, the file's URI does not change, so further manipulation of the file (like permanently deleting or restoring it) is still performed by accessing this URI.

### Restoring a file from the trash ###

Restoring a file from the trash can be done by making a POST request to the file's URI, adding the parameter "restore" with an empty value (restore=). If the user is not allowed to restore the file, HTTP status code 405 (Method Not Allowed) will be returned.

### Working with older versions ###

```
http://hostname/gss/rest/aaitest@uth.gr/files/Documents/doc.txt?version=1
```

When a file is versioned, we can retrieve older versions of its content by supplying a query parameter to the file URI, as shown above. A GET request on this URI returns the specified file, with the proper HTTP response headers set appropriately:

```
    Etag: "812-1232701053318"
    Last-Modified: Wed, 22 Oct 2008 15:40:45 GMT
    Content-Length: 802
    Content-Type: text/plain
    X-GSS-Metadata: {
            "name": "doc.txt",
            "creationDate": 1232449958563,
            "createdBy": "aaitest@uth.gr",
            "readForAll": true,
            "modifiedBy": "aaitest@uth.gr",
            "owner": "aaitest@uth.gr",
            "modificationDate": 1232449944444,
            "deleted": false,
            "versioned": true,
            "version": 1,
            "size": 802,
            "content": "text/plain",
            "uri": "http://hostname/gss/rest/aaitest2@uth.gr/files/Documents/doc.txt",
            "folder": {
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/",
                "name": "Documents"
            },
            "path": "/Documents/",
            "tags": [
                "work",
                "personal"
            ],
            "permissions": [
                {
                    "modifyACL": true,
                    "write": true,
                    "read": true,
                    "user": "past@ebs.gr"
                },
                {
                    "modifyACL": false,
                    "write": true,
                    "read": true,
                    "group": "EBS"
                }
            ]
        } 
```

Here we have received the modified size of the file (802 bytes), the requested version number (version: 1), that version's last modification time and the relevant Etag. The discussion above about Range, If- headers, etc. applies here, too.

Restoring older versions of a file can be performed by making a POST request to the file URI, supplying the parameter `restoreVersion` with a value of the version number to be restored. This would create a new version with the contents of the file in the specified version. Subsequent GETs to the file URI will return this new version.

## Working with the trash can ##

Retrieving the contents of the trash bin can be very time-consuming, since it would require many requests to establish the deleted resources (with "deleted": true) in the user's namespace, before being able to populate the trash can view. In order to alleviate this problem, there is a shortcut provided by the "trash" namespace:

```
http://hostname/gss/rest/aaitest@uth.gr/trash
```

This read-only namespace returns the top-level contents of the user's trash bin:

```
    {
        "files": [
            {
                "name": "doc.txt",
                "owner": "aaitest@uth.gr",
                "creationDate": 1233758218866,
                "modificationDate": 1233758218866,
                "deleted":true,
                "size":161,
                "content": "text/plain",
                "shared":false,
                "versioned":true,
                "version": 1,
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                    "name": "aaitest"
                },
                "path": "/",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/doc.txt"
            }
        ],
        "folders": [
            {
                "name": "Documents",
                "parent": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents"
            }
        ]
    }
```

After retrieving the top-level contents of the trash can, the client application can display the "top-level" deleted files and folders (the ones that are not contained in deleted folders). If the user making the request is not the same as the user whose trash contents are being requested, HTTP status code 405 (Method Not Allowed) will be returned. If the trash can is empty, HTTP status code 204 (No Content) will be returned.

Emptying the trash can is performed by sending a DELETE command to the trash URI. Successful execution is denoted by an HTTP status code 204 (No Content). If the user trying to empty the trash can is not the same as the owner of the specified namespace, HTTP status 405 (Method Not Allowed) is returned.

## Sharing files and folders ##

```
http://hostname/gss/rest/aaitest@uth.gr/shared
```

Files the user has shared with others. A GET request in this URI returns the following response:

```
    {
        "files": [
            {
                "name": "sha1.js",
                "owner": "aaitest@uth.gr",
                "creationDate": 1233758218866,
                "modificationDate": 1233758218866,
                "deleted":false,
                "size":1610,
                "content": "application/javascript",
                "versioned":true,
                "version": 1,
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                    "name": "aaitest"
                },
                "path": "/",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/sha1.js"
            },
            {
                "name": "foo.txt",
                "owner": "aaitest@uth.gr",
                "creationDate": 1233758229944,
                "modificationDate": 1233758318866,
                "deleted":false,
                "content": "text/plain",
                "versioned":true,
                "size":2610,
                "version": 1,
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/",
                    "name": "Documents"
                },
                "path": "/Documents/",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/foo.txt"
            }
        ],
        "folders": [
            {
                "name": "Documents",
                "parent": "http://hostname/gss/rest/aaitest@uth.gr/files/",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/"
            },
            {
                "name": "test",
                "parent": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/",
                "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/test/"
            }
        ]
    }
```

If the user making the request is not the same as the user whose shared resources are being requested, HTTP status code 405 (Method Not Allowed) will be returned.

```
http://hostname/gss/rest/aaitest@uth.gr/others
```

Retrieves the other users who have shared files or folders with the current user. A GET request in this URI returns the following response:

```
    [
        {
            "username": "aaitest2@uth.gr",
            "uri": "http://hostname/gss/rest/aaitest@uth.gr/others/aaitest2@uth.gr"
        }
    ]
```

A new GET request to the above URI will return the following JSON response:

```
    {
        "files": [
            {
                "name": "photo.gif",
                "owner": "aaitest2@uth.gr",
                "creationDate": 1233758218866,
                "modificationDate": 1233758218866,
                "deleted":false,
                "size":1640,
                "content": "image/gif",
                "shared":false,
                "versioned":true,
                "version": 1,
                "folder": {
                    "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Demo/",
                    "name": "Demo"
                },
                "path": "/Demo/",
                "uri": "http://hostname/gss/rest/aaitest2@uth.gr/files/Demo/photo.gif"
            }
        ],
        "folders": [
            {
                "name": "Documents",
                "uri": "http://hostname/gss/rest/aaitest2@uth.gr/files/Documents/"
            }
        ]
    }
```

The response contains an object with two attributes, "files" and "folders", that contain the collections of shared files and folders respectively. These resources are the top-most resources in the other user's file namespace that have been shared to this user. Shared folders and files contained in another shared folder, will not be present in this response. In other words, it's the client's responsibility to retrieve other shared files and folders contained in a retrieved folder, when the user requests navigation inside this folder. The API is designed to support navigational use cases that are common in file system-like applications. Ad-hoc queries that might be useful to other kinds of applications are not catered for, and the onus of implementing them is left on the shoulders of the client application developer.

If the user making the request is not the same as the user whose 'others' namespace is being requested, HTTP status code 405 (Method Not Allowed) will be returned.

## Retrieving the defined tags ##

When a user defines tags for files, these tags are visible and therefore meaningful only to that particular user. In order to retrieve all the tags defined by the user, a GET request to the following URI should be made:

```
http://hostname/gss/rest/aaitest@uth.gr/tags
```

This would retrieve the file tags specified by the user, generating the following response:

```
    [
        "work",
        "JavaScript",
        "personal",
        "code"
    ]
```

A client application could present the defined tags to the user in order to aid the process of tagging a new file. If the user making the request is not the same as the user whose tags are being requested, HTTP status code 405 (Method Not Allowed) will be returned.

## Working with groups ##

```
http://hostname/gss/rest/aaitest@uth.gr/groups
```

A GET request in this URI returns a JSON array with the groups the user has created:

```
    [
        {
            "name": "Friends",
            "uri": "http://hostname/gss/rest/aaitest@uth.gr/groups/Friends"
        },
        {
            "name": "Work",
            "uri": "http://hostname/gss/rest/aaitest@uth.gr/groups/Work"
        }
    ]
```

A POST request in this URI will create a new group with the name specified in the parameter "name", returning the HTTP status code 201 (Created).

```
http://hostname/gss/rest/aaitest@uth.gr/groups/Friends
```

The members of the "Friends" group.

```
    [
        "http://hostname/gss/rest/aaitest@uth.gr/groups/Friends/aaitest2@uth.gr"
    ]
```

A POST request in this URI will add a new user to the group, with the username specified in the parameter "name", returning the HTTP status code 201 (Created). If the specified user cannot be found, HTTP status 404 (Not found) will be returned. A DELETE request in this URI will remove the specified group, returning the HTTP status code 204 (No Content). If the user is not allowed to remove this group, HTTP status code 405 (Method Not Allowed) will be returned.

```
http://hostname/gss/rest/aaitest@uth.gr/groups/Friends/aaitest2@uth.gr
```

A member of the "Friends" group.

```
    {
        "username": "aaitest2@uth.gr",
        "name": "aaitest2",
        "home": "http://hostname/gss/rest/aaitest2@uth.gr"
    }
```

A DELETE request in this URI will remove the specified user from the group, returning the HTTP status code 204 (No Content). If the user is not allowed to remove this group member, HTTP status code 405 (Method Not Allowed) will be returned.


# Searching for files #

```
http://hostname/gss/rest/search/web%20services
```

A GET request in this URI performs a search query for "web services". Only GET is allowed inside the search URI namespace. Two request parameters are allowed. The _start_ parameter indicates two things: a) Not all results will be returned but only the number specified in the _searchResultsPerPage_ property and b)the offset of the results (e.g. if start=10, results from the 10th onwards will be returned). The _lucene_ parameter indicates that the query string will not be escaped and will be treated as a lucene formatted query. The response contains an array with the individual files that matched the query, like the following:

```
[
    {
        "name": "testxls.xls",
        "owner": "aaitest@uth.gr",
        "creationDate": 1233758218866,
        "deleted": false,
        "size": 16134,
        "content": "application/vnd.ms-excel",
        "shared":false,
        "versioned":true,
        "version": 4,
        "folder": {
            "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/",
            "name": "aaitest"
        },
        "path": "/",
        "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/testxls.xls"
    },
    {
        "name": "testdoc.doc",
        "owner": "aaitest@uth.gr",
        "creationDate": 1233758229944,
        "deleted": false,
        "size": 2610,
        "content": "application/msword",
        "shared":false,
        "versioned":true,
        "version": 1,
        "folder": {
            "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/",
            "name": "Documents"
        },
        "path": "/Documents/",
        "uri": "http://hostname/gss/rest/aaitest@uth.gr/files/Documents/testdoc.doc"
    }
]
```

If the start parameter is present then the first object of the array will be
```
    {
        "length":120
    }
```

indicating the total number of results.

# Searching for users #

```
http://hostname/gss/rest/users/aaitest
```

A GET request in this URI performs a search query for users with usernames starting with "aaitest". Only GET is allowed inside the user search URI namespace and the response contains an array with the individual users that matched the query, like the following:

```
[
    {
        "username": "aaitest@uth.gr",
        "name": "aaitest",
        "home": "http://hostname/gss/rest/aaitest@uth.gr"
    },
    {
        "username": "aaitest@ntua.gr",
        "name": "aaitest",
        "home": "http://hostname/gss/rest/aaitest@ntua.gr"
    }
]
```

In order to mitigate privacy concerns, this API method will only accept queries that contain the full username of the user, up to the @ character contained in every username returned by Shibboleth. In other words, searching for `aai` whill actually search for usernames starting with aai@ and therefore fail to match `aaitest@uth.gr` or `aaitest@ntua.gr`. This behavior is controlled by a flag in the [server-side code](http://code.google.com/p/gss/source/browse/trunk/gss/src/gr/ebs/gss/server/rest/UserSearchHandler.java).

# Requesting a new token #

```
http://hostname/gss/rest/newtoken
```

A GET request in this URI instructs the service to immediately invalidate the current authentication token and return a new one. Only GET is allowed for this URI, without any additional PATH\_INFO elements or request parameters and the response contains the new authentication token. Note that the token binary value has been base64-encoded in order to be transferred along with the response. Therefore it needs to be converted back to binary form by the client before use.

This method could be used by clients in order to avoid bothering the user with authentication pages, like for instance from a command-line client.