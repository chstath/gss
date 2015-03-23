# Short version #

```
ant install run
```

# Long version #

## Prerequisites ##

Building GSS requires the following software to be installed on your system:

  * Java Development Kit version 1.6.
  * Apache Ant version 1.7.

Running GSS requires a running PostgreSQL server for storing the system's data. Setting up PostgreSQL for gss entails configuring the database to accept connections from the application server and creating the appropriate database and user:

```
# create user gss with password 'gss';
# create database gssdb owner gss encoding 'UTF8';
```


## Installing and configuring dependencies ##

Run "ant install" from the gss directory to download the various software dependencies (JBoss, GWT, etc.) and install the configuration files. The install task is something that will need to be executed again only if the configuration files or dependencies change. This requirement will be explicitly mentioned in the ChangeLog after a new release.


## Building and running the server ##

Run "ant run" from the gss directory to build and deploy gss and start JBoss. Use Ctrl/Cmd-C to stop the server. If you want to rebuild and redeploy the service after having made changes to the source code, run "ant deploy" or "ant" without a task name, which does the same thing.

On first launch the database will be empty. If you have successfully setup Shibboleth authentication (see the [Authentication](BuildingAndDeploying#Authentication.md) section below) you may use existing user credentials to log into the service. If the Shibboleth IdP is itself empty, visit the following URL:

```
http://127.0.0.1:8080/pithos/register
```

There you will be able to both register a new account with the service and create a new account in the configured IdP/LDAP server. See the gss.properties configuration file for more information on configuring the gss-ldap connection.


## Customizing the build script ##

If you want to customize the behavior of the ant script, you may want to create a build.properties file in the gss directory with the following variables, set to the appropriate values for your system.

build.properties:

```
jboss.home=/opt/jboss-5.1.0.GA
jboss.args=-b 0.0.0.0 -Djboss.server.log.threshold=DEBUG
gwt.workers=4
```

The value of jboss.home should be the path where jboss will be installed and run from. The value of jboss.args will get passed to the JBoss startup script when starting the service with "ant run". The value of gwt.workers should match the virtual cores in your system for faster builds. For instance in a dual-core system the best value would be 2. You can experiment with increasing the value until you start to get worse build times, if you are not sure about your system's specifications.

Alternatively, you can override the above properties using command line arguments:

```
ant -Djboss.home=/usr/local/jboss-5.1.0.GA -Dgwt.workers=2
```


## Production deployment ##

First of all, modify the configuration files to suit your installation. Most importantly go through the gss.properties file and remove the testUsername value, since you probably won't need a loophole in your authentication system. Then make sure that the permissions in the jboss directory and subdirectories are properly set for the system user that the service will run as.

Copy the jboss init script to the proper place for the host operating system. Modify the various variables defined in the start of the run script as necessary.

For Debian:

```
# cp /path/to/production/deployment/jboss-5.1.0.GA/bin/jboss_init_debian.sh /etc/init.d/jboss
# chmod +x /etc/init.d/jboss
# update-rc.d jboss defaults
```


## Installing the indexing service ##

Download the Solr 1.3.0 binary from one of its mirrors and then download and install the patch mentioned in [this](http://wiki.apache.org/solr/UpdateRichDocuments) page.

Follow the instructions on How To Install to patch Solr with the file rich.patch.
Copy the solr init script to the proper place for the host operating system. Modify the various variables defined in the start of the run script as necessary.

For Debian:

```
# cp solr/bin/solr /etc/init.d/solr
# chmod +x /etc/init.d/solr
# update-rc.d solr defaults
```


## Authentication ##

You need to have Shibboleth authentication set up in order to authenticate. The first step in the Shibboleth authentication process is a redirection to a WAYF (Where Are You From) server, for selecting the Identity Provider that has your credentials. You can read more about Shibboleth [here](http://en.wikipedia.org/wiki/Shibboleth_(Internet2)).

Then point a browser to [http://127.0.0.1:8080/pithos/](http://127.0.0.1:8080/pithos/) in order to connect to the service.

Alternatively, for development purposes, you can use a shortcut in the server to authenticate without Shibboleth. You have to register a new account (see [Building and running the server](BuildingAndDeploying#Building_and_running_the_server.md) section above) or manually initialize a user entry in the database first (using SQL insert) and then set its username in the testUsername property of the jboss\conf\gss.properties configuration file. You can find it deployed in JBOSS\_HOME\server\default\conf\gss.properties. The proper URL for using in this scenario is the following, which can be also found in the GWT hosted mode launch configuration:

[http://127.0.0.1:8080/pithos/login?next=http://127.0.0.1:8080/pithos/](http://127.0.0.1:8080/pithos/login?next=http://127.0.0.1:8080/pithos/)