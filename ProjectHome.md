GSS is a scalable file storage service, built on open source technologies that offers an open API and multiple user interfaces (rich web GUI, WebDAV, Desktop client). GSS is built on Java.

It supports the following functionality:
  * upload files and organize them in hierarchical folder structure within each user's private space
  * full-text search and tagging
  * allow sharing of files & folders among users and groups, as well as public (unauthenticated) access to specific files
  * trashbin functionality
  * file versioning (optional per file)
  * Shibboleth user authentication (http://shibboleth.internet2.edu/)
  * offer a REST-like API for interfacing with third-party applications
  * support WebDAV access
  * rich web GUI client (access via the REST API)
  * desktop client (access via the REST API)

GSS consists of the following components:
  * cluster of GSS servers (handles main functionality and API)
  * Solr server(s) (index and search functionality)
  * DB server (stores meta-data)
  * rich GUI web client based on GWT

![http://gss.googlecode.com/svn/trunk/gss/docs/userguide/el/images/gss_main.png](http://gss.googlecode.com/svn/trunk/gss/docs/userguide/el/images/gss_main.png)

&lt;wiki:gadget url="http://www.ohloh.net/p/316590/widgets/project\_basic\_stats.xml" height="220" border="1"/&gt;