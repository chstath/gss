<%--

Copyright 2009 Electronic Business Systems Ltd.
 
This file is part of GSS.

GSS is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

GSS is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GSS.  If not, see <http://www.gnu.org/licenses/>.

--%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<%@page import="org.gss_project.gss.server.configuration.GSSConfigurationFactory"%><html>
<head>
<title><%= GSSConfigurationFactory.getConfiguration().getString("serviceName") %> Terms and Conditions</title>
<link href="/pithos/main.css" rel="stylesheet" type="text/css">
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
</head>
<body>
<div class="wrapper" >
<div class="header"></div>
<div class="image_logo">
<table><tr>
  <td><a href="/"><img src="/pithos/images/service-logo.png"></img></a>
</tr></table>
</div> <!-- image_logo -->
<div style="clear: both; "> </div>
<div class="page_main">
<center>
<form method="post" action="/pithos/policy">
<input type="hidden" name="user" value="<%= request.getParameter("user") %>">
<input type="hidden" name="queryString" value="<%= request.getQueryString() %>">
<h1><%= GSSConfigurationFactory.getConfiguration().getString("serviceName") %> Terms and Conditions</h1>
<p>Before using <%= GSSConfigurationFactory.getConfiguration().getString("serviceName") %> you must accept the service <a href='/terms' target='_blank'>Terms and Conditions</a>.
<p><input type="checkbox" name="accept" title="Accept terms and conditions">
I have read and understood the Terms &amp; Conditions mentioned herein above and agree to abide by them
<p><button type="submit" name="button" value="register" >Submit</button></p>
</form>
</center>
</div>
<div class="footer"></div>
</div> <!-- wrapper -->
</body>
</html>
