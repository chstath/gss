# Introduction #

This page contains instructions for connecting to gss using the webdav interface.

For webdav access use the credentials shown in the web client (https://domainname/gss) by selecting (Menu) 'Settigns' -> 'Show Credentials'. Use the username and password displayed in the pop up dialog.

# Windows #
## Windows 7/Vista ##
  * In Computer / My Computer select "Map network drive"
  * In the address field type https://domainname/webdav/
  * Might need to check 'Connect using different credentials'
  * Fill in the username & password and save them

Note that the windows native client:
  * does not display your quota correctly
  * if you reboot windows the webdav drive is not automatically mounted, but if you click on the explorer disconnected drive it then mounts without problem
  * in some windows versions by default explorer does not allow download of files larger than 50MB from webdav folders. To overcame that you must add **FileSizeLimitInBytes** (type DWORD) in the registry under HKEY\_LOCAL\_MACHINE\SYSTEM\CurrentControlSet\services\WebClient\Parameters and give the appropriate value in bytes.

## Windows XP ##
You need Windows XP SP3 and you must mount webdav as a web folders drive. In the "Map network drive" dialog click on the link "Sign up for online storage or connect to a network server.", below the address input field. Fill-in the same credentials mentioned above

## Windows webdav 3rd party client apps ##
Using a 3rd party client application resolves a lot of the native windows problems, especially in Windows XP. NetDrive http://www.netdrive.net/ has been tested successfully with GSS.


# Ubuntu #

The nautilus implementation for dav access is broken so in order to properly use the webdav interface we need to use davfs

  * Install davfs2: sudo apt-get install davfs2
  * Create a folder to mount gss e.g. sudo mkdir /media/gss
  * Add an entry to your etc/fstab containing the following: https://domainname/webdav /media/gss davfs rw,user,noauto 0 0
  * change permissions of mount.davfs by issuing the following command sudo chmod u+s /sbin/mount.davfs
  * edit /etc/davfs2/davfs2.conf find the line containing ignore\_home  and add ignore\_home kernoops
  * finally add the user to the group named davfs2

You can now connect to gss by issuing mount /media/gss from command line or using Places from nautilus