The design of GSS was not treated lightly. There are several design decisions that stem from a desire to follow the REST architecture principles and the HTTP protocol specification. Since many of those decisions are not very well documented, here is a list of frequently asked questions about design and operational issues that come up often.


  1. [FAQ#Why\_not\_use\_the\_e-mail\_address\_of\_the\_user\_as\_the\_username?](FAQ#Why_not_use_the_e-mail_address_of_the_user_as_the_username?.md)
  1. [FAQ#Do\_resource\_URIs\_require\_a\_trailing\_slash?](FAQ#Do_resource_URIs_require_a_trailing_slash?.md)
  1. [FAQ#When\_creating\_a\_folder\_what\_is\_the\_proper\_path\_of\_the\_resource\_t](FAQ#When_creating_a_folder_what_is_the_proper_path_of_the_resource_t.md)
  1. [FAQ#Why\_are\_my\_POST\_requests\_failing?](FAQ#Why_are_my_POST_requests_failing?.md)
  1. [FAQ#Can\_I\_upload\_a\_file\_in\_a\_non-existent\_path?](FAQ#Can_I_upload_a_file_in_a_non-existent_path?.md)
  1. [FAQ#Why\_not\_have\_the\_server\_create\_all\_the\_parent\_directories\_for\_me](FAQ#Why_not_have_the_server_create_all_the_parent_directories_for_me.md)
  1. [FAQ#Perhaps\_404\_would\_be\_a\_better\_error\_code\_for\_such\_a\_case?](FAQ#Perhaps_404_would_be_a_better_error_code_for_such_a_case?.md)


### Why not use the e-mail address of the user as the username? ###

Since GSS uses Shibboleth as the identity provider, it must abide by its constraints. In a Shibboleth federation the e-mail attribute is not guaranteed to be present in every user account, unlike the eduPersonPrincipalName attribute which is used in GSS. Various organizations in the federation may choose to omit e-mail assignment or avoid publishing these attributes to the GSS SP. Moreover, even when it is present it may contain multiple values with no clear distinction about which is the preferred one.

### Do resource URIs require a trailing slash? ###

Common sense should work well here: folders in the file system may or may not have trailing slashes, files should not. In general, trailing slashes are optional, with the exception of file URIs where they are forbidden. This holds for every namespace, like files, shared, trash, etc. A bug currently exists in the handling of the user namespace without a trailing slash, so requests for /rest/username/ must end with a slash.

### When creating a folder what is the proper path of the resource to use when generating the signature: (i) `/username/files/`, (ii) `/username/files/foo` or (iii) `/username/files/?new=foo`? ###

The correct path is (i). The second path is non-existent at the time of creation and the third one contains request parameters which are not part of the path. Bear in mind that failing to pick the correct resource path will result in HTTP status 403 (Forbidden) from the server.

### Why are my POST requests failing? ###

Make sure that you use the proper Content-Type header:
```
Content-Type: application/x-www-form-urlencoded;
```
Also note that the server is permissive in what it accepts and passing form parameters as request parameters (like this: `/username/files/?new=foo`) will work, too.

### Can I upload a file in a non-existent path? ###

No, the destination folder must already exist, otherwise an HTTP status 409 (Conflict) will be returned.

### Why not have the server create all the parent directories for me in such a case? ###

The HTTP protocol specification (RFC 2616, paragraph 9.6) notes:

> If the Request-URI does not point to an existing resource, and that URI is capable of being defined as a new resource by the requesting user agent, the origin server can create the resource with that URI.

The issue here is that when the parent path does not exist, the PUT request to upload a file will result in the creation of two or more resources, the file and the parent directory or directories. However, as noted in the protocol specification (paragraph 9.1.2), PUT is an idempotent method and two consecutive PUT requests must not have different side effects, such as the creation of parent folders.

### Perhaps 404 would be a better error code for such a case? ###

That would seem reasonable, but the protocol specification in paragraph 10.4.5 notes about the 404 (Not Found) response:

> The server has not found anything matching the Request-URI. No indication is given of whether the condition is temporary or permanent.

In other words status 404 is returned when the requested URI is not found, not another URI which would correspond to the parent folder in this case. Bear in mind that URIs should be considered opaque. The specification is more broad about the use of HTTP status 409 (Conflict), as we see in paragraph 10.4.10:

> The request could not be completed due to a conflict with the current state of the resource. This code is only allowed in situations where it is expected that the user might be able to resolve the conflict and resubmit the request.

It may not be too obvious, but there aren't any better error codes available for this kind of error. You can think about it as a conflict between what the client thinks is there (a parent directory) and what the server knows to be present.