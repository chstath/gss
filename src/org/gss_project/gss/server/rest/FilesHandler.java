/*
 * Copyright 2008, 2009, 2010 Electronic Business Systems Ltd.
 *
 * This file is part of GSS.
 *
 * GSS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GSS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GSS.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gss_project.gss.server.rest;

import static org.gss_project.gss.server.configuration.GSSConfigurationFactory.getConfiguration;
import org.gss_project.gss.common.exceptions.DuplicateNameException;
import org.gss_project.gss.common.exceptions.GSSIOException;
import org.gss_project.gss.common.exceptions.InsufficientPermissionsException;
import org.gss_project.gss.common.exceptions.ObjectNotFoundException;
import org.gss_project.gss.common.exceptions.QuotaExceededException;
import org.gss_project.gss.common.exceptions.RpcException;
import org.gss_project.gss.server.Login;
import org.gss_project.gss.server.domain.FileBody;
import org.gss_project.gss.server.domain.FileHeader;
import org.gss_project.gss.server.domain.FileUploadStatus;
import org.gss_project.gss.server.domain.Folder;
import org.gss_project.gss.server.domain.Group;
import org.gss_project.gss.server.domain.Permission;
import org.gss_project.gss.server.domain.User;
import org.gss_project.gss.server.ejb.ExternalAPI;
import org.gss_project.gss.server.ejb.TransactionHelper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * A class that handles operations on the 'files' namespace.
 *
 * @author past
 */
public class FilesHandler extends RequestHandler {
	/**
	 * The request parameter name for fetching a different version.
	 */
	private static final String VERSION_PARAM = "version";

	/**
	 * The request attribute containing the owner of the destination URI
	 * in a copy or move request.
	 */
	private static final String DESTINATION_OWNER_ATTRIBUTE = "destOwner";

	private static final int TRACK_PROGRESS_PERCENT = 5;

	/**
	 * The form parameter name that contains the signature in a browser POST upload.
	 */
	private static final String AUTHORIZATION_PARAMETER = "Authorization";

	/**
	 * The form parameter name that contains the date in a browser POST upload.
	 */
	private static final String DATE_PARAMETER = "Date";

	/**
	 * The request parameter name for making an upload progress request.
	 */
	private static final String PROGRESS_PARAMETER = "progress";

	/**
	 * The request parameter name for restoring a previous version of a file.
	 */
	private static final String RESTORE_VERSION_PARAMETER = "restoreVersion";

	/**
	 * The logger.
	 */
	private static Log logger = LogFactory.getLog(FilesHandler.class);

	/**
	 * The servlet context provided by the call site.
	 */
	private ServletContext context;

	/**
	 * The style sheet for displaying the directory listings.
	 */
	private static final String GSS_CSS = "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " + "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " + "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " + "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " + "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " + "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}" + "A {color : black;}" + "A.name {color : black;}" + "HR {color : #525D76;}";


	/**
	 * @param servletContext
	 */
	public FilesHandler(ServletContext servletContext) {
		context = servletContext;
	}

	private void updateAccounting(final User user, final Date date, final long bandwidthDiff) {
		try {
			new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					getService().updateAccounting(user, date, bandwidthDiff);
					return null;
				}
			});
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			// updateAccounting() doesn't throw any checked exceptions
			assert false;
		}
	}

	/**
     * Serve the specified resource, optionally including the data content.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     * @param content Should the content be included?
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     * @throws RpcException
     * @throws InsufficientPermissionsException
     * @throws ObjectNotFoundException
     */
	@Override
	protected void serveResource(HttpServletRequest req, HttpServletResponse resp, boolean content)
    		throws IOException, ServletException {
		boolean authDeferred = getAuthDeferred(req);
        String path = getInnerPath(req, PATH_FILES);
		if (path.equals(""))
			path = "/";
		try {
			path = URLDecoder.decode(path, "UTF-8");
		} catch (IllegalArgumentException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		}
    	String progress = req.getParameter(PROGRESS_PARAMETER);

    	if (logger.isDebugEnabled())
			if (content)
    			logger.debug("Serving resource '" +	path + "' headers and data");
    		else
    			logger.debug("Serving resource '" +	path + "' headers only");

    	User user = getUser(req);
    	User owner = getOwner(req);
        boolean exists = true;
        Object resource = null;
        FileHeader file = null;
        Folder folder = null;
        try {
        	resource = getService().getResourceAtPath(owner.getId(), path, false);
        } catch (ObjectNotFoundException e) {
            exists = false;
        } catch (RpcException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

    	if (!exists && authDeferred) {
    		// We do not want to leak information if the request
    		// was not authenticated.
    		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
    		return;
    	}

    	if (resource instanceof Folder)
    		folder = (Folder) resource;
    	else
    		file = (FileHeader) resource;	// Note that file will be null, if (!exists).

    	// Now it's time to perform the deferred authentication check.
		// Since regular signature checking was already performed,
		// we need to check the read-all flag or the signature-in-parameters.
		if (authDeferred) {
			if (file != null && !file.isReadForAll() && content) {
				// Check for GET with the signature in the request parameters.
				String auth = req.getParameter(AUTHORIZATION_PARAMETER);
				String dateParam = req.getParameter(DATE_PARAMETER);
				if (auth == null || dateParam == null) {
					// Check for a valid authentication cookie.
					if (req.getCookies() != null) {
						boolean found = false;
						for (Cookie cookie : req.getCookies())
							if (Login.AUTH_COOKIE.equals(cookie.getName())) {
								String cookieauth = cookie.getValue();
								int sepIndex = cookieauth.indexOf(Login.COOKIE_SEPARATOR);
								if (sepIndex == -1) {
									handleAuthFailure(req, resp);
									return;
								}
								String username = URLDecoder.decode(cookieauth.substring(0, sepIndex), "US-ASCII");
								user = null;
								try {
									user = getService().findUser(username);
								} catch (RpcException e) {
						        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
									return;
								}
								if (user == null) {
						    		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
						    		return;
						    	}
								req.setAttribute(USER_ATTRIBUTE, user);
								String token = cookieauth.substring(sepIndex + 1);
								if (user.getAuthToken() == null) {
									resp.sendError(HttpServletResponse.SC_FORBIDDEN);
									return;
								}
								if (!Arrays.equals(user.getAuthToken(), Base64.decodeBase64(token))) {
									resp.sendError(HttpServletResponse.SC_FORBIDDEN);
									return;
								}
								found = true;
								break;
							}
						if (!found) {
							handleAuthFailure(req, resp);
							return;
						}
					} else {
						handleAuthFailure(req, resp);
						return;
					}
				} else {
			    	long timestamp;
					try {
						timestamp = DateUtil.parseDate(dateParam).getTime();
					} catch (DateParseException e) {
			    		resp.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			    		return;
					}

					// Fetch the Authorization parameter and find the user specified in it.
					String[] authParts = auth.split(" ");
					if (authParts.length != 2) {
			    		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			    		return;
			    	}
					String username = authParts[0];
					String signature = authParts[1];
					user = null;
					try {
						user = getService().findUser(username);
					} catch (RpcException e) {
			        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
						return;
					}
					if (user == null) {
			    		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			    		return;
			    	}
					req.setAttribute(USER_ATTRIBUTE, user);

					// Remove the servlet path from the request URI.
					String p = req.getRequestURI();
					String servletPath = req.getContextPath() + req.getServletPath();
					p = p.substring(servletPath.length());
					// Validate the signature in the Authorization parameter.
					String data = req.getMethod() + dateParam + p;
					if (!isSignatureValid(signature, user, data)) {
			    		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			    		return;
			    	}
				}
			}
			else if(folder != null && folder.isReadForAll() || file != null && file.isReadForAll()){
				//This case refers to a folder or file with public privileges
				//For a read-for-all folder request, pretend the owner is making it.
				user = owner;
				req.setAttribute(USER_ATTRIBUTE, user);
			}else if(folder != null && !folder.isReadForAll()){
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			else{
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		}
    	// If the resource is not a collection, and the resource path
    	// ends with "/" or "\", return NOT FOUND.
    	if (folder == null)
			if (path.endsWith("/") || path.endsWith("\\")) {
    			resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
    			return;
    		}

    	// Workaround for IE's broken caching behavior.
    	if (folder != null)
    		resp.setHeader("Expires", "-1");

    	// A request for upload progress.
    	if (progress != null && content) {
    		serveProgress(req, resp, progress, user, file);
			return;
    	}

		// Fetch the version to retrieve, if specified.
		String verStr = req.getParameter(VERSION_PARAM);
		int version = 0;
		FileBody oldBody = null;
		if (verStr != null && file != null)
			try {
				version = Integer.valueOf(verStr);
			} catch (NumberFormatException e) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, req.getRequestURI());
    			return;
			}
		if (version > 0)
			try {
				oldBody = getService().getFileVersion(user.getId(), file.getId(), version);
			} catch (RpcException e) {
	        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
				return;
			} catch (ObjectNotFoundException e) {
    			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    			return;
			} catch (InsufficientPermissionsException e) {
    			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    			return;
			}

    	// Check if the conditions specified in the optional If headers are
    	// satisfied. Doing this for folders would require recursive checking
    	// for all of their children, which in turn would defy the purpose of
    	// the optimization.
    	if (folder == null){
			// Checking If headers.
    		if (!checkIfHeaders(req, resp, file, oldBody))
				return;
    	}
    	else if(!checkIfModifiedSince(req, resp, folder))
    		return;

    	// Find content type.
    	String contentType = null;
    	boolean isContentHtml = false;
    	boolean expectJSON = false;

    	if (file != null) {
        	contentType = version>0 ? oldBody.getMimeType() : file.getCurrentBody().getMimeType();
        	if (contentType == null) {
        		contentType = context.getMimeType(file.getName());
        		file.getCurrentBody().setMimeType(contentType);
        	}
    	} else { // folder != null
    		String accept = req.getHeader("Accept");
    		// The order in this conditional pessimizes the common API case,
    		// but is important for backwards compatibility with existing
    		// clients who send no accept header and expect a JSON response.
    		if (accept != null && accept.contains("text/html")) {
    			contentType = "text/html;charset=UTF-8";
    			isContentHtml = true;
    			//this is the case when clients send the appropriate headers, the contentType is "text/html"
    			//and expect a JSON response. The above check applies to FireGSS client
    			expectJSON = !authDeferred ? true : false;
    		}
            else if (authDeferred && req.getMethod().equals(METHOD_GET)) {
                contentType = "text/html;charset=UTF-8";
                isContentHtml = true;
                expectJSON = false;
            }
    		else {
    			contentType = "application/json;charset=UTF-8";
    			expectJSON = true;
    		}
		}


    	ArrayList ranges = null;
    	long contentLength = -1L;

    	if (file != null) {
    		// Parse range specifier.
    		ranges = parseRange(req, resp, file, oldBody);
    		// ETag header
    		resp.setHeader("ETag", getETag(file, oldBody));
    		// Last-Modified header.
    		String lastModified = oldBody == null ?
    					getLastModifiedHttp(file.getAuditInfo()) :
    					getLastModifiedHttp(oldBody.getAuditInfo());
    		resp.setHeader("Last-Modified", lastModified);
    		// X-GSS-Metadata header.
    		try {
				resp.setHeader("X-GSS-Metadata", renderJson(user, file, oldBody));
			} catch (InsufficientPermissionsException e) {
				resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	        	return;
	        }
    		// Get content length.
    		contentLength = version>0 ? oldBody.getFileSize() : file.getCurrentBody().getFileSize();
    		// Special case for zero length files, which would cause a
    		// (silent) ISE when setting the output buffer size.
    		if (contentLength == 0L)
				content = false;
    	} else
    		// Set the folder X-GSS-Metadata header.
    		try {
				resp.setHeader("X-GSS-Metadata", renderJsonMetadata(user, folder));
			} catch (InsufficientPermissionsException e) {
				resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	        	return;
	        }

    	ServletOutputStream ostream = null;
    	PrintWriter writer = null;

    	if (content)
			try {
    			ostream = resp.getOutputStream();
    		} catch (IllegalStateException e) {
    			// If it fails, we try to get a Writer instead if we're
    			// trying to serve a text file
    			if ( contentType == null
    						|| contentType.startsWith("text")
    						|| contentType.endsWith("xml") )
					writer = resp.getWriter();
				else
					throw e;
    		}
    	if (folder != null || (ranges == null || ranges.isEmpty()) && req.getHeader("Range") == null || ranges == FULL) {
    		// Set the appropriate output headers
    		if (contentType != null) {
    			if (logger.isDebugEnabled())
    				logger.debug("contentType='" + contentType + "'");
    			resp.setContentType(contentType);
    		}
    		if (file != null && contentLength >= 0) {
    			if (logger.isDebugEnabled())
    				logger.debug("contentLength=" + contentLength);
    			if (contentLength < Integer.MAX_VALUE)
					resp.setContentLength((int) contentLength);

				else
					// Set the content-length as String to be able to use a long
    				resp.setHeader("content-length", "" + contentLength);
    		}

    		InputStream renderResult = null;
    		String relativePath = getRelativePath(req);
    		String contextPath = req.getContextPath();
    		String servletPath = req.getServletPath();
    		String contextServletPath = contextPath + servletPath;
    		if (folder != null && content)
    			// Serve the directory browser for a public folder
    			if (isContentHtml && !expectJSON) {
                    try {
                        folder = getService().expandFolder(folder);
                    }
                    catch (ObjectNotFoundException e) {
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
                        return;
                    }
                    catch (RpcException e) {
                        //We send 500 instead of 404 because this folder has been loaded before in this method and it is
                        //impossible to not be found now
        	            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			            return;
		            }
                    renderResult = renderHtml(contextServletPath, relativePath, folder,user);
                }
    			// Serve the directory for an ordinary folder or for fireGSS client
    			else
    				try {
    					renderResult = renderJson(user, folder);
    					} catch (InsufficientPermissionsException e) {
    						resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    						return;
    					}


    		// Copy the input stream to our output stream (if requested)
    		if (content) {
    			try {
    				resp.setBufferSize(output);
    			} catch (IllegalStateException e) {
    				// Silent catch
    			}
    			try {
    				if(file != null)
						if (needsContentDisposition(req))
    						resp.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+getDispositionFilename(file));
    					else
    						resp.setHeader("Content-Disposition","inline; filename*=UTF-8''"+getDispositionFilename(file));
	    			if (ostream != null)
						copy(file, renderResult, ostream, req, oldBody);
					else
						copy(file, renderResult, writer, req, oldBody);
	    			if (file!=null) updateAccounting(owner, new Date(), contentLength);
        		} catch (ObjectNotFoundException e) {
        			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        			return;
        		} catch (InsufficientPermissionsException e) {
        			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        			return;
        		} catch (RpcException e) {
        			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        			return;
	    		}
    		}
    	} else {
    		if (ranges == null || ranges.isEmpty())
    			return;
    		// Partial content response.
    		resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

    		if (ranges.size() == 1) {
    			Range range = (Range) ranges.get(0);
    			resp.addHeader("Content-Range", "bytes "
    						+ range.start
    						+ "-" + range.end + "/"
    						+ range.length);
    			long length = range.end - range.start + 1;
    			if (length < Integer.MAX_VALUE)
					resp.setContentLength((int) length);
				else
					// Set the content-length as String to be able to use a long
    				resp.setHeader("content-length", "" + length);

    			if (contentType != null) {
    				if (logger.isDebugEnabled())
    					logger.debug("contentType='" + contentType + "'");
    				resp.setContentType(contentType);
    			}

    			if (content) {
    				try {
    					resp.setBufferSize(output);
    				} catch (IllegalStateException e) {
    					// Silent catch
    				}
    				try {
	    				if (ostream != null)
							copy(file, ostream, range, req, oldBody);
						else
							copy(file, writer, range, req, oldBody);
	    				updateAccounting(owner, new Date(), contentLength);
    	    		} catch (ObjectNotFoundException e) {
    	    			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    	    			return;
    	    		} catch (InsufficientPermissionsException e) {
    	    			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    	    			return;
    	    		} catch (RpcException e) {
    	    			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    	    			return;
    	    		}
    			}
    		} else {
    			resp.setContentType("multipart/byteranges; boundary=" + mimeSeparation);
    			if (content) {
    				try {
    					resp.setBufferSize(output);
    				} catch (IllegalStateException e) {
    					// Silent catch
    				}
    				try {
	    				if (ostream != null)
							copy(file, ostream, ranges.iterator(), contentType, req, oldBody);
						else
							copy(file, writer, ranges.iterator(), contentType, req, oldBody);
	    				updateAccounting(owner, new Date(), contentLength);
    	    		} catch (ObjectNotFoundException e) {
    	    			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    	    			return;
    	    		} catch (InsufficientPermissionsException e) {
    	    			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    	    			return;
    	    		} catch (RpcException e) {
    	    			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    	    			return;
    	    		}
    			}
    		}
    	}
    }

	/**
	 * Handles an authentication failure. If no Authorization or Date request
	 * parameters and no Authorization, Date or X-GSS-Date headers were present,
	 * this is a browser request, so redirect to login and then let the user get
	 * back to the file. Otherwise it's a bogus client request and Forbidden is
	 * returned.
	 */
	private void handleAuthFailure(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (req.getParameter(AUTHORIZATION_PARAMETER) == null &&
				req.getParameter(DATE_PARAMETER) == null &&
				req.getHeader(AUTHORIZATION_HEADER) == null &&
				req.getDateHeader(DATE_HEADER) == -1 &&
				req.getDateHeader(GSS_DATE_HEADER) == -1)
			resp.sendRedirect(getConfiguration().getString("loginUrl") +
					"?next=" + req.getRequestURL().toString());
		else
			resp.sendError(HttpServletResponse.SC_FORBIDDEN);
	}

	/**
	 * Return the filename of the specified file properly formatted for
	 * including in the Content-Disposition header.
	 */
	private String getDispositionFilename(FileHeader file) throws UnsupportedEncodingException {
		return URLEncoder.encode(file.getName(),"UTF-8").replaceAll("\\+", "%20");
	}

	/**
	 * Determines whether the user agent needs the Content-Disposition
	 * header to be set, in order to properly download a file.
	 *
	 * @param req the HTTP request
	 * @return true if the Content-Disposition HTTP header must be set
	 */
	private boolean needsContentDisposition(HttpServletRequest req) {
		/*String agent = req.getHeader("user-agent");
		if (agent != null && agent.contains("MSIE"))
			return true;*/
		String dl = req.getParameter("dl");
		if ("1".equals(dl))
			return true;
		return false;
	}

	/**
	 * Sends a progress update on the amount of bytes received until now for
	 * a file that the current user is currently uploading.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param parameter the value for the progress request parameter
	 * @param user the current user
	 * @param file the file being uploaded, or null if the request is about a new file
	 * @throws IOException if an I/O error occurs
	 */
	private void serveProgress(HttpServletRequest req, HttpServletResponse resp,
				String parameter, User user, FileHeader file)	throws IOException {
		String filename = file == null ? parameter : file.getName();
		try {
			FileUploadStatus status = getService().getFileUploadStatus(user.getId(), filename);
			if (status == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			JSONObject json = new JSONObject();
			json.put("bytesUploaded", status.getBytesUploaded()).
				put("bytesTotal", status.getFileSize());
			sendJson(req, resp, json.toString());

			// Workaround for IE's broken caching behavior.
    		resp.setHeader("Expires", "-1");
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		} catch (JSONException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			return;
		}
	}

	/**
	 * Server a POST request to create/modify a file or folder.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
     * @exception IOException if an input/output error occurs
	 */
	void postResource(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		boolean authDeferred = getAuthDeferred(req);
    	if (!authDeferred && req.getParameterMap().size() > 1) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
    		return;
    	}
        String path = getInnerPath(req, PATH_FILES);
    	path = path.endsWith("/")? path: path + '/';
		try {
	    	path = URLDecoder.decode(path, "UTF-8");
		} catch (IllegalArgumentException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		}
    	// We only defer authenticating multipart POST requests.
    	if (authDeferred) {
			if (!ServletFileUpload.isMultipartContent(req)) {
	    		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
	    		return;
	    	}
			handleMultipart(req, resp, path);
			return;
		}

    	String newName = req.getParameter(NEW_FOLDER_PARAMETER);

    	boolean hasUpdateParam = req.getParameterMap().containsKey(RESOURCE_UPDATE_PARAMETER);
    	boolean hasTrashParam = req.getParameterMap().containsKey(RESOURCE_TRASH_PARAMETER);
    	boolean hasRestoreParam = req.getParameterMap().containsKey(RESOURCE_RESTORE_PARAMETER);
    	String copyTo = req.getParameter(RESOURCE_COPY_PARAMETER);
    	String moveTo = req.getParameter(RESOURCE_MOVE_PARAMETER);
    	String restoreVersion = req.getParameter(RESTORE_VERSION_PARAMETER);

    	if (newName != null){
        	if (!isValidResourceName(newName)) {
        		resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        		return;
        	}
			createFolder(req, resp, path, newName);
    	}
    	else if (hasUpdateParam)
			updateResource(req, resp, path);
		else if (hasTrashParam)
			trashResource(req, resp, path);
		else if (hasRestoreParam)
			restoreResource(req, resp, path);
		else if (copyTo != null)
			copyResource(req, resp, path, copyTo);
		else if (moveTo != null)
			moveResource(req, resp, path, moveTo);
		else if (restoreVersion != null)
			restoreVersion(req, resp, path, restoreVersion);
		else
			// IE with Gears uses POST for multiple uploads.
			putResource(req, resp);
	}

	/**
	 * Restores a previous version for a file.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the resource path
	 * @param version the version number to restore
	 * @throws IOException if an I/O error occurs
	 */
	private void restoreVersion(HttpServletRequest req, HttpServletResponse resp, String path, String version) throws IOException {
		final User user = getUser(req);
		User owner = getOwner(req);
		Object resource = null;
		try {
			resource = getService().getResourceAtPath(owner.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
		if (resource instanceof Folder) {
			resp.sendError(HttpServletResponse.SC_CONFLICT);
			return;
		}

		try {
			final FileHeader file = (FileHeader) resource;
			final int oldVersion = Integer.parseInt(version);

			new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					getService().restoreVersion(user.getId(), file.getId(), oldVersion);
					return null;
				}
			});
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
		} catch (GSSIOException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (QuotaExceededException e) {
			resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
		} catch (NumberFormatException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/**
	 * A method for handling multipart POST requests for uploading
	 * files from browser-based JavaScript clients.
	 *
	 * @param request the HTTP request
	 * @param response the HTTP response
	 * @param path the resource path
	 * @throws IOException in case an error occurs writing to the
	 * 		response stream
	 */
	private void handleMultipart(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
    	if (logger.isDebugEnabled())
   			logger.debug("Multipart POST for resource: " + path);

    	User owner = getOwner(request);
    	boolean exists = true;
        Object resource = null;
        FileHeader file = null;
        try {
        	resource = getService().getResourceAtPath(owner.getId(), path, false);
        } catch (ObjectNotFoundException e) {
            exists = false;
        } catch (RpcException e) {
        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

        if (exists)
			if (resource instanceof FileHeader) {
    			file = (FileHeader) resource;
    			if (file.isDeleted()) {
    				response.sendError(HttpServletResponse.SC_CONFLICT, file.getName() + " is in the trash");
    	    		return;
    			}
			} else {
	        	response.sendError(HttpServletResponse.SC_CONFLICT, path + " is a folder");
	    		return;
	        }

    	Object parent;
    	String parentPath = null;
		try {
			parentPath = getParentPath(path);
			parent = getService().getResourceAtPath(owner.getId(), parentPath, true);
		} catch (ObjectNotFoundException e) {
    		response.sendError(HttpServletResponse.SC_NOT_FOUND, parentPath);
    		return;
		} catch (RpcException e) {
        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
    	if (!(parent instanceof Folder)) {
    		response.sendError(HttpServletResponse.SC_CONFLICT);
    		return;
    	}
    	final Folder folderLocal = (Folder) parent;
    	final String fileName = getLastElement(path);

    	if (!isValidResourceName(fileName)) {
    		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    		return;
    	}

		FileItemIterator iter;
		File uploadedFile = null;
		try {
			// Create a new file upload handler.
			ServletFileUpload upload = new ServletFileUpload();
			StatusProgressListener progressListener = new StatusProgressListener(getService());
			upload.setProgressListener(progressListener);
			iter = upload.getItemIterator(request);
			String dateParam = null;
			String auth = null;
			while (iter.hasNext()) {
				FileItemStream item = iter.next();
				String name = item.getFieldName();
				InputStream stream = item.openStream();
				if (item.isFormField()) {
					final String value = Streams.asString(stream);
					if (name.equals(DATE_PARAMETER))
						dateParam = value;
					else if (name.equals(AUTHORIZATION_PARAMETER))
						auth = value;

					if (logger.isDebugEnabled())
						logger.debug(name + ":" + value);
				} else {
					// Fetch the timestamp used to guard against replay attacks.
			    	if (dateParam == null) {
			    		response.sendError(HttpServletResponse.SC_FORBIDDEN, "No Date parameter");
			    		return;
			    	}

			    	long timestamp;
					try {
						timestamp = DateUtil.parseDate(dateParam).getTime();
					} catch (DateParseException e) {
			    		response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			    		return;
					}

					// Fetch the Authorization parameter and find the user specified in it.
			    	if (auth == null) {
			    		response.sendError(HttpServletResponse.SC_FORBIDDEN, "No Authorization parameter");
			    		return;
			    	}
					String[] authParts = auth.split(" ");
					if (authParts.length != 2) {
			    		response.sendError(HttpServletResponse.SC_FORBIDDEN);
			    		return;
			    	}
					String username = authParts[0];
					String signature = authParts[1];
					User user = null;
					try {
						user = getService().findUser(username);
					} catch (RpcException e) {
			        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
						return;
					}
					if (user == null) {
			    		response.sendError(HttpServletResponse.SC_FORBIDDEN);
			    		return;
			    	}
					request.setAttribute(USER_ATTRIBUTE, user);

					// Remove the servlet path from the request URI.
					String p = request.getRequestURI();
					String servletPath = request.getContextPath() + request.getServletPath();
					p = p.substring(servletPath.length());
					// Validate the signature in the Authorization parameter.
					String data = request.getMethod() + dateParam + p;
					if (!isSignatureValid(signature, user, data)) {
			    		response.sendError(HttpServletResponse.SC_FORBIDDEN);
			    		return;
			    	}

					progressListener.setUserId(user.getId());
					progressListener.setFilename(fileName);
					final String contentType = item.getContentType();

					try {
						uploadedFile = getService().uploadFile(stream, user.getId());
					} catch (IOException ex) {
						throw new GSSIOException(ex, false);
					}
					FileHeader fileLocal = null;
					final File upf = uploadedFile;
					final FileHeader f = file;
					final User u = user;
					if (file == null)
						fileLocal = new TransactionHelper<FileHeader>().tryExecute(new Callable<FileHeader>() {
							@Override
							public FileHeader call() throws Exception {
								return getService().createFile(u.getId(), folderLocal.getId(), fileName, contentType, upf.getCanonicalFile().length(), upf.getAbsolutePath());
							}
						});
					else
						fileLocal = new TransactionHelper<FileHeader>().tryExecute(new Callable<FileHeader>() {
							@Override
							public FileHeader call() throws Exception {
								return getService().updateFileContents(u.getId(), f.getId(), contentType, upf.getCanonicalFile().length(), upf.getAbsolutePath());
							}
						});
					updateAccounting(owner, new Date(), fileLocal.getCurrentBody().getFileSize());
					getService().removeFileUploadProgress(user.getId(), fileName);
				}
			}
			// We can't return 204 here since GWT's onSubmitComplete won't fire.
			response.setContentType("text/html");
            response.getWriter().print("<pre></pre>");
		} catch (FileUploadException e) {
			String error = "Error while uploading file";
			logger.error(error, e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
		} catch (GSSIOException e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "Error while uploading file";
			if (e.logAsError())
				logger.error(error, e);
			else
				logger.debug(error, e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
		} catch (DuplicateNameException e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "The specified file name already exists in this folder";
			logger.error(error, e);
			response.sendError(HttpServletResponse.SC_CONFLICT, error);

		} catch (InsufficientPermissionsException e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "You don't have the necessary permissions";
			logger.error(error, e);
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, error);

		} catch (QuotaExceededException e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "Not enough free space available";
			if (logger.isDebugEnabled())
				logger.debug(error, e);
			response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, error);

		} catch (ObjectNotFoundException e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "A specified object was not found";
			logger.error(error, e);
			response.sendError(HttpServletResponse.SC_NOT_FOUND, error);
		} catch (RpcException e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "An error occurred while communicating with the service";
			logger.error(error, e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
		} catch (Exception e) {
			if (uploadedFile != null && uploadedFile.exists())
				uploadedFile.delete();
			String error = "An internal server error occurred";
			logger.error(error, e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
		}
	}

	/**
	 * Move the resource in the specified path to the specified destination.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the path of the resource
	 * @param moveTo the destination of the move procedure
	 * @throws IOException if an input/output error occurs
	 */
	private void moveResource(HttpServletRequest req, HttpServletResponse resp, String path, String moveTo) throws IOException {
		final User user = getUser(req);
		User owner = getOwner(req);
		Object resource = null;
		try {
			resource = getService().getResourceAtPath(owner.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

        String destination = null;
        User destOwner = null;
		boolean exists = true;
		try {
			destination = getDestinationPath(req, encodePath(moveTo));
			destination = URLDecoder.decode(destination, "UTF-8");
			destOwner = getDestinationOwner(req);
			getService().getResourceAtPath(destOwner.getId(), destination, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (URISyntaxException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, destination);
			return;
		}
		if (exists) {
			resp.sendError(HttpServletResponse.SC_CONFLICT, destination + " already exists");
			return;
		}

		try {
			final User dOwner = destOwner;
			final String dest = destination;
			if (resource instanceof Folder) {
				final Folder folderLocal = (Folder) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().moveFolderToPath(user.getId(), dOwner.getId(), folderLocal.getId(), dest);
						return null;
					}
				});
			} else {
				final FileHeader fileLocal = (FileHeader) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().moveFileToPath(user.getId(), dOwner.getId(), fileLocal.getId(), dest);
						return null;
					}
				});

			}
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, destination);
		} catch (DuplicateNameException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (GSSIOException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (QuotaExceededException e) {
			resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, destination);
		}
	}

	/**
	 * Copy the resource in the specified path to the specified destination.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the path of the resource
	 * @param copyTo the destination of the copy procedure
	 * @throws IOException if an input/output error occurs
	 */
	private void copyResource(HttpServletRequest req, HttpServletResponse resp, String path, String copyTo) throws IOException {
		final User user = getUser(req);
		User owner = getOwner(req);
		Object resource = null;
		try {
			resource = getService().getResourceAtPath(owner.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

        String destination = null;
        User destOwner = null;
		boolean exists = true;
		try {
			String destinationEncoded = getDestinationPath(req, encodePath(copyTo));
			destination = URLDecoder.decode(destinationEncoded, "UTF-8");
			destOwner = getDestinationOwner(req);
			getService().getResourceAtPath(destOwner.getId(), destinationEncoded, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (URISyntaxException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, destination);
			return;
		}
		if (exists) {
			resp.sendError(HttpServletResponse.SC_CONFLICT, destination + " already exists");
			return;
		}

		try {
			final User dOwner = destOwner;
			final String dest = destination;
			if (resource instanceof Folder) {
				final Folder folderLocal = (Folder) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().copyFolderStructureToPath(user.getId(), dOwner.getId(), folderLocal.getId(), dest);
						return null;
					}
				});
			} else {
				final FileHeader fileLocal = (FileHeader) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().copyFileToPath(user.getId(), dOwner.getId(), fileLocal.getId(), dest);
						return null;
					}
				});
			}
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, destination);
		} catch (DuplicateNameException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (GSSIOException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (QuotaExceededException e) {
			resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, destination);
		}
	}

	private String encodePath(String path) throws UnsupportedEncodingException{
		StringTokenizer str = new StringTokenizer(path, "/:", true);
		String result = new String();
		while(str.hasMoreTokens()){
			String token = str.nextToken();
			if(!token.equals("/") && !token.equals(":"))
				token = URLEncoder.encode(token,"UTF-8");
			result = result + token;
		}
		return result;
	}
	/**
	 * A helper method that extracts the relative resource path,
	 * after removing the 'files' namespace.
	 * The path returned is <i>not</i> URL-decoded.
	 *
	 * @param req the HTTP request
	 * @param path the specified path
	 * @return the path relative to the root folder
	 * @throws URISyntaxException
	 * @throws RpcException in case an error occurs while communicating
	 * 						with the backend
	 * @throws UnsupportedEncodingException
	 */
	private String getDestinationPath(HttpServletRequest req, String path) throws URISyntaxException, RpcException, UnsupportedEncodingException {
		URI uri = new URI(path);
		String dest = uri.getRawPath();
		// Remove the context path from the destination URI.
		String contextPath = req.getContextPath();
		if (!dest.startsWith(contextPath))
			throw new URISyntaxException(dest, "Destination path does not start with " + contextPath);
		dest = dest.substring(contextPath.length());
		// Remove the servlet path from the destination URI.
		String servletPath = req.getServletPath();
		if (!dest.startsWith(servletPath))
			throw new URISyntaxException(dest, "Destination path does not start with " + servletPath);
		dest = dest.substring(servletPath.length());
    	// Strip the username part
		if (dest.length() < 2)
			throw new URISyntaxException(dest, "No username in the destination URI");
		int slash = dest.substring(1).indexOf('/');
		if (slash == -1)
			throw new URISyntaxException(dest, "No username in the destination URI");
		// Decode the user to get the proper characters (mainly the @)
		String owner = URLDecoder.decode(dest.substring(1, slash + 1), "UTF-8");
		User o;
		o = getService().findUser(owner);
		if (o == null)
			throw new URISyntaxException(dest, "User " + owner + " not found");

		req.setAttribute(DESTINATION_OWNER_ATTRIBUTE, o);
		dest = dest.substring(slash + 1);

		// Chop the resource namespace part
		dest = dest.substring(RequestHandler.PATH_FILES.length());

    	dest = dest.endsWith("/")? dest: dest + '/';
		return dest;
	}

	/**
	 * Move the resource in the specified path to the trash bin.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the path of the resource
	 * @throws IOException if an input/output error occurs
	 */
	private void trashResource(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
		final User user = getUser(req);
		User owner = getOwner(req);
		Object resource = null;
		try {
			resource = getService().getResourceAtPath(owner.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

		try {
			if (resource instanceof Folder) {
				final Folder folderLocal = (Folder) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().moveFolderToTrash(user.getId(), folderLocal.getId());
						return null;
					}
				});
			} else {
				final FileHeader fileLocal = (FileHeader) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().moveFileToTrash(user.getId(), fileLocal.getId());
						return null;
					}
				});
			}
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
		}
	}

	/**
	 * Restore the resource in the specified path from the trash bin.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the path of the resource
	 * @throws IOException if an input/output error occurs
	 */
	private void restoreResource(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
		final User user = getUser(req);
		User owner = getOwner(req);
		Object resource = null;
		try {
			resource = getService().getResourceAtPath(owner.getId(), path, false);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

		try {
			if (resource instanceof Folder) {
				final Folder folderLocal = (Folder) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().removeFolderFromTrash(user.getId(), folderLocal.getId());
						return null;
					}
				});
			} else {
				final FileHeader fileLocal = (FileHeader) resource;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().removeFileFromTrash(user.getId(), fileLocal.getId());
						return null;
					}
				});
			}
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
		}
	}

	/**
	 * Update the resource in the specified path.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the path of the resource
	 * @throws IOException if an input/output error occurs
	 */
	private void updateResource(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
		final User user = getUser(req);
		User owner = getOwner(req);
		Object resource = null;

		try {
			resource = getService().getResourceAtPath(owner.getId(), path, false);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
		StringBuffer input = new StringBuffer();
		JSONObject json = null;
		if (req.getContentType() != null && req.getContentType().startsWith("application/x-www-form-urlencoded"))
			input.append(req.getParameter(RESOURCE_UPDATE_PARAMETER));
		else {
			// Assume application/json
			BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream(),"UTF-8"));
			String line = null;
			while ((line = reader.readLine()) != null)
				input.append(line);
			reader.close();
		}
		try {
			json = new JSONObject(input.toString());
			if (logger.isDebugEnabled())
				logger.debug("JSON update: " + json);
			if (resource instanceof Folder) {
				final Folder folderLocal = (Folder) resource;
				String name = json.optString("name");
				if (!isValidResourceName(name)) {
	        		resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
	        		return;
	        	}
				JSONArray permissions = json.optJSONArray("permissions");
				Set<Permission> perms = null;
				if (permissions != null)
					perms = parsePermissions(user, permissions);
				Boolean readForAll = null;
				if (json.opt("readForAll") != null)
					readForAll = json.optBoolean("readForAll");
				if (!name.isEmpty() || permissions != null || readForAll != null) {
					final String fName = name.isEmpty()? null: name;
					final Boolean freadForAll =  readForAll;
					final Set<Permission> fPerms = perms;
					Folder folderUpdated = new TransactionHelper<Folder>().tryExecute(new Callable<Folder>() {
						@Override
						public Folder call() throws Exception {
							return getService().updateFolder(user.getId(), folderLocal.getId(), fName, freadForAll, fPerms);
						}

					});
					resp.getWriter().println(getNewUrl(req, folderUpdated));
				}
			} else {
				final FileHeader fileLocal = (FileHeader) resource;
				String name = null;
				if (json.opt("name") != null)
					name = json.optString("name");
				if (name != null)
					if (!isValidResourceName(name)) {
		        		resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
		        		return;
		        	}
				Long modificationDate = null;
				if (json.optLong("modificationDate") != 0)
					modificationDate = json.optLong("modificationDate");
				Boolean versioned = null;
				if (json.opt("versioned") != null)
					versioned = json.getBoolean("versioned");
				JSONArray tagset = json.optJSONArray("tags");
				String tags = null;
				StringBuffer t = new StringBuffer();
				if (tagset != null) {
					for (int i = 0; i < tagset.length(); i++)
						t.append(tagset.getString(i) + ',');
					tags = t.toString();
				}
				JSONArray permissions = json.optJSONArray("permissions");
				Set<Permission> perms = null;
				if (permissions != null)
					perms = parsePermissions(user, permissions);
				Boolean readForAll = null;
				if (json.opt("readForAll") != null)
					readForAll = json.optBoolean("readForAll");
				if (name != null || tags != null || modificationDate != null
							|| versioned != null || perms != null
							|| readForAll != null) {
					final String fName = name;
					final String fTags = tags;
					final Date mDate = modificationDate != null? new Date(modificationDate): null;
					final Boolean fVersioned = versioned;
					final Boolean fReadForAll = readForAll;
					final Set<Permission> fPerms = perms;
					new TransactionHelper<Object>().tryExecute(new Callable<Object>() {
						@Override
						public Object call() throws Exception {
							getService().updateFile(user.getId(), fileLocal.getId(),
										fName, fTags, mDate, fVersioned,
										fReadForAll, fPerms);
							return null;
						}

					});
				}
			}
		} catch (JSONException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (DuplicateNameException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
	}

	/**
	 * Returns the new URL of an updated folder.
	 */
	private String getNewUrl(HttpServletRequest req, Folder folder) throws UnsupportedEncodingException {
		String parentUrl = URLDecoder.decode(getContextPath(req, true),"UTF-8");
		String fpath = URLDecoder.decode(getRelativePath(req), "UTF-8");
		if (parentUrl.indexOf(fpath) != -1)
			parentUrl = parentUrl.substring(0, parentUrl.indexOf(fpath));
		if(!parentUrl.endsWith("/"))
			parentUrl = parentUrl+"/";
		parentUrl = parentUrl+folder.getOwner().getUsername()+PATH_FILES+folder.getPath();
		return parentUrl;
	}

	/**
	 * Helper method to convert a JSON array of permissions into a set of
	 * Permission objects.
	 *
	 * @param user the current user
	 * @param permissions the JSON array to parse
	 * @return the parsed set of permissions
	 * @throws JSONException if there was an error parsing the JSON object
	 * @throws RpcException if there was an error communicating with the EJB
	 * @throws ObjectNotFoundException if the user could not be found
	 * @throws UnsupportedEncodingException
	 */
	private Set<Permission> parsePermissions(User user, JSONArray permissions)
			throws JSONException, RpcException, ObjectNotFoundException, UnsupportedEncodingException {
		if (permissions == null)
			return null;
		Set<Permission> perms = new HashSet<Permission>();
		for (int i = 0; i < permissions.length(); i++) {
			JSONObject j = permissions.getJSONObject(i);
			Permission perm = new Permission();
			perm.setModifyACL(j.optBoolean("modifyACL"));
			perm.setRead(j.optBoolean("read"));
			perm.setWrite(j.optBoolean("write"));
			String permUser = j.optString("user");
			if (!permUser.isEmpty()) {
				User u = getService().findUser(permUser);
				if (u == null)
					throw new ObjectNotFoundException("User " + permUser + " not found");
				perm.setUser(u);
			}
			// 31/8/2009: Add optional groupUri which takes priority if it exists
			String permGroupUri = j.optString("groupUri");
			String permGroup = j.optString("group");
			if (!permGroupUri.isEmpty()) {
				String[] names = permGroupUri.split("/");
				String grp = URLDecoder.decode(names[names.length - 1], "UTF-8");
				String usr = URLDecoder.decode(names[names.length - 3], "UTF-8");
				User u = getService().findUser(usr);
				if (u == null)
					throw new ObjectNotFoundException("User " + permUser + " not found");
				Group g = getService().getGroup(u.getId(), grp);
				perm.setGroup(g);
			}
			else if (!permGroup.isEmpty()) {
				Group g = getService().getGroup(user.getId(), permGroup);
				perm.setGroup(g);
			}
			if (permUser.isEmpty() && permGroupUri.isEmpty() && permGroup.isEmpty())
				throw new JSONException("A permission must correspond to either a user or a group");
			perms.add(perm);
		}
		return perms;
	}

	/**
	 * Creates a new folder with the specified name under the folder in the provided path.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param path the parent folder path
	 * @param folderName the name of the new folder
	 * @throws IOException if an input/output error occurs
	 */
	private void createFolder(HttpServletRequest req, HttpServletResponse resp, String path, final String folderName) throws IOException {
		if (logger.isDebugEnabled())
   			logger.debug("Creating folder " + folderName + " in '" + path);

    	final User user = getUser(req);
    	User owner = getOwner(req);
        boolean exists = true;
        try {
        	getService().getResourceAtPath(owner.getId(), path + folderName, false);
        } catch (ObjectNotFoundException e) {
            exists = false;
        } catch (RpcException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path + folderName);
			return;
		}

        if (exists) {
            resp.addHeader("Allow", METHOD_GET + ", " + METHOD_DELETE +
            			", " + METHOD_HEAD);
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

		Object parent;
		try {
			parent = getService().getResourceAtPath(owner.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path + folderName);
			return;
		}
		try {
			if (parent instanceof Folder) {
				final Folder folderLocal = (Folder) parent;
				Folder newFolder = new TransactionHelper<Folder>().tryExecute(new Callable<Folder>() {
					@Override
					public Folder call() throws Exception {
						return getService().createFolder(user.getId(), folderLocal.getId(), folderName);
					}

				});
	        	String newResource = getApiRoot() + newFolder.getURI();
	        	resp.setHeader("Location", newResource);
	        	resp.setContentType("text/plain");
	    	    PrintWriter out = resp.getWriter();
	    	    out.println(newResource);
			} else {
				resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	    		return;
			}
		} catch (DuplicateNameException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT);
    		return;
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    		return;
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path + folderName);
			return;
		} catch (Exception e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
    	resp.setStatus(HttpServletResponse.SC_CREATED);
	}

	/**
	 * @param req
	 * @param resp
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	void putResource(HttpServletRequest req, HttpServletResponse resp) throws IOException, FileNotFoundException {
        String path = getInnerPath(req, PATH_FILES);
		try {
	    	path = URLDecoder.decode(path, "UTF-8");
		} catch (IllegalArgumentException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		}
    	if (logger.isDebugEnabled())
   			logger.debug("Updating resource: " + path);

    	final User user = getUser(req);
    	User owner = getOwner(req);
    	boolean exists = true;
        Object resource = null;
        FileHeader fileLocal = null;
        try {
        	resource = getService().getResourceAtPath(owner.getId(), path, false);
        } catch (ObjectNotFoundException e) {
            exists = false;
        } catch (RpcException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

        if (exists)
			if (resource instanceof FileHeader)
    			fileLocal = (FileHeader) resource;
			else {
	        	resp.sendError(HttpServletResponse.SC_CONFLICT, path + " is a folder");
	    		return;
	        }
        boolean result = true;

        // Temporary content file used to support partial PUT.
        File contentFile = null;

        Range range = parseContentRange(req, resp);

        InputStream resourceInputStream = null;

        // Append data specified in ranges to existing content for this
        // resource - create a temporary file on the local filesystem to
        // perform this operation.
        // Assume just one range is specified for now
        if (range != null) {
            try {
				contentFile = executePartialPut(req, range, path);
			} catch (RpcException e) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
				return;
			} catch (ObjectNotFoundException e) {
				resp.sendError(HttpServletResponse.SC_CONFLICT);
        		return;
			} catch (InsufficientPermissionsException e) {
				resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        		return;
			}
            resourceInputStream = new FileInputStream(contentFile);
        } else
			resourceInputStream = req.getInputStream();

        try {
        	Folder folderLocal = null;
        	Object parent = getService().getResourceAtPath(owner.getId(), getParentPath(path), true);
        	if (!(parent instanceof Folder)) {
        		resp.sendError(HttpServletResponse.SC_CONFLICT);
        		return;
        	}
       		folderLocal = (Folder) parent;
        	final String name = getLastElement(path);
        	final String mimeType = context.getMimeType(name);
        	File uploadedFile = null;
        	try {
				uploadedFile = getService().uploadFile(resourceInputStream, user.getId());
			} catch (IOException ex) {
				throw new GSSIOException(ex, false);
			}
        	FileHeader fileTemp = null;
        	final File uploadedf = uploadedFile;
			final Folder parentf = folderLocal;
			final FileHeader f = fileLocal;
            if (exists)
            	fileTemp = new TransactionHelper<FileHeader>().tryExecute(new Callable<FileHeader>() {
					@Override
					public FileHeader call() throws Exception {
						return getService().updateFileContents(user.getId(), f.getId(), mimeType, uploadedf.getCanonicalFile().length(), uploadedf.getAbsolutePath());
					}
				});
			else
				fileTemp = new TransactionHelper<FileHeader>().tryExecute(new Callable<FileHeader>() {
					@Override
					public FileHeader call() throws Exception {
						return getService().createFile(user.getId(), parentf.getId(), name, mimeType, uploadedf.getCanonicalFile().length(), uploadedf.getAbsolutePath());
					}

				});
            updateAccounting(owner, new Date(), fileTemp.getCurrentBody().getFileSize());
			getService().removeFileUploadProgress(user.getId(), fileTemp.getName());
        } catch(ObjectNotFoundException e) {
            result = false;
        } catch (RpcException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
        } catch (IOException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		} catch (GSSIOException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		} catch (DuplicateNameException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT);
    		return;
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    		return;
		} catch (QuotaExceededException e) {
			resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
    		return;
		} catch (Exception e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

        if (result) {
            if (exists)
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			else
				resp.setStatus(HttpServletResponse.SC_CREATED);
        } else
			resp.sendError(HttpServletResponse.SC_CONFLICT);
	}

    /**
     * Delete a resource.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are processing
	 * @throws IOException if the response cannot be sent
     */
    void deleteResource(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = getInnerPath(req, PATH_FILES);
    	if (logger.isDebugEnabled())
   			logger.debug("Deleting resource '" + path);
    	path = URLDecoder.decode(path, "UTF-8");
    	final User user = getUser(req);
    	User owner = getOwner(req);
    	boolean exists = true;
    	Object object = null;
    	try {
    		object = getService().getResourceAtPath(owner.getId(), path, false);
    	} catch (ObjectNotFoundException e) {
    		exists = false;
    	} catch (RpcException e) {
    		resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

    	if (!exists) {
    		resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    		return;
    	}

    	Folder folderLocal = null;
    	FileHeader fileLocal = null;
    	if (object instanceof Folder)
    		folderLocal = (Folder) object;
    	else
    		fileLocal = (FileHeader) object;

    	if (fileLocal != null)
			try {
				final FileHeader f = fileLocal;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().deleteFile(user.getId(), f.getId());
						return null;
					}
				});
	        } catch (InsufficientPermissionsException e) {
	        	resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				return;
    		} catch (ObjectNotFoundException e) {
    			// Although we had already found the object, it was
    			// probably deleted from another thread.
    			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    			return;
    		} catch (RpcException e) {
    			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    			return;
    		} catch (Exception e) {
    			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    			return;
    		}
		else if (folderLocal != null)
			try {
				final Folder fo = folderLocal;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().deleteFolder(user.getId(), fo.getId());
						return null;
					}
				});
	        } catch (InsufficientPermissionsException e) {
	        	resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	        	return;
    		} catch (ObjectNotFoundException e) {
	        	resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
	        	return;
    		} catch (RpcException e) {
	        	resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	        	return;
    		} catch (Exception e) {
	        	resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	        	return;
    		}
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    	return;
    }

	/**
     * Return an InputStream to a JSON representation of the contents
     * of this directory.
     *
	 * @param user the user that made the request
     * @param folder the specified directory
     * @return an input stream with the rendered contents
	 * @throws IOException if the response cannot be sent
     * @throws ServletException
	 * @throws InsufficientPermissionsException if the user does not have
	 * 			the necessary privileges to read the directory
     */
    private InputStream renderJson(User user, Folder folder) throws IOException,
    		ServletException, InsufficientPermissionsException {
    	try {
			folder = getService().expandFolder(folder);
		} catch (ObjectNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (RpcException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	JSONObject json = new JSONObject();
    	try {
			json.put("name", folder.getName()).
					put("owner", folder.getOwner().getUsername()).
					put("createdBy", folder.getAuditInfo().getCreatedBy().getUsername()).
					put("creationDate", folder.getAuditInfo().getCreationDate().getTime()).
					put("deleted", folder.isDeleted()).
					put("shared", folder.getShared()).
					put("readForAll", folder.isReadForAll());

			if (folder.getAuditInfo().getModifiedBy() != null)
				json.put("modifiedBy", folder.getAuditInfo().getModifiedBy().getUsername()).
						put("modificationDate", folder.getAuditInfo().getModificationDate().getTime());
			if (folder.getParent() != null) {
				JSONObject j = new JSONObject();
				j.put("uri", getApiRoot() + folder.getParent().getURI());
				j.put("name", folder.getParent().getName());
				json.put("parent", j);
			}
	    	List<JSONObject> subfolders = new ArrayList<JSONObject>();
	    	for (Folder f: folder.getSubfolders())
				if (!f.isDeleted()) {
					JSONObject j = new JSONObject();
					j.put("name", f.getName()).
						put("uri", getApiRoot() + f.getURI()).
						put("shared", f.getShared());
					subfolders.add(j);
				}
	    	json.put("folders", subfolders);
	    	List<JSONObject> files = new ArrayList<JSONObject>();
	    	List<FileHeader> fileHeaders = getService().getFiles(user.getId(), folder.getId(), false);
	    	for (FileHeader f: fileHeaders) {
	    		JSONObject j = new JSONObject();
				j.put("name", f.getName()).
					put("owner", f.getOwner().getUsername()).
					put("deleted", f.isDeleted()).
					put("version", f.getCurrentBody().getVersion()).
					put("content", f.getCurrentBody().getMimeType()).
					put("size", f.getCurrentBody().getFileSize()).
					put("shared", f.getShared()).
					put("versioned",f.isVersioned()).
					put("creationDate", f.getAuditInfo().getCreationDate().getTime()).
					put("path", f.getFolder().getPath()).
					put("uri", getApiRoot() + f.getURI());
				if (f.getAuditInfo().getModificationDate() != null)
					j.put("modificationDate", f.getAuditInfo().getModificationDate().getTime());
				files.add(j);
	    	}
	    	json.put("files", files);
	    	Set<Permission> perms = getService().getFolderPermissions(user.getId(), folder.getId());
	    	json.put("permissions", renderJson(perms));
		} catch (JSONException e) {
			throw new ServletException(e);
		} catch (ObjectNotFoundException e) {
			throw new ServletException(e);
		} catch (RpcException e) {
			throw new ServletException(e);
		}

    	// Prepare a writer to a buffered area
    	ByteArrayOutputStream stream = new ByteArrayOutputStream();
    	OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
    	PrintWriter writer = new PrintWriter(osWriter);

    	// Return an input stream to the underlying bytes
    	writer.write(json.toString());
    	writer.flush();
    	return new ByteArrayInputStream(stream.toByteArray());
    }

	/**
     * Return a String with a JSON representation of the metadata
     * of the specified folder.
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
     */
    private String renderJsonMetadata(User user, Folder folder)
    		throws ServletException, InsufficientPermissionsException {
    	// Check if the user has read permission.
		try {
			if (!getService().canReadFolder(user.getId(), folder.getId()))
				throw new InsufficientPermissionsException();
		} catch (ObjectNotFoundException e) {
			throw new ServletException(e);
		} catch (RpcException e) {
			throw new ServletException(e);
		}

    	JSONObject json = new JSONObject();
    	try {
			json.put("name", URLEncoder.encode(folder.getName(), "UTF-8")).
			put("owner", folder.getOwner().getUsername()).
			put("createdBy", folder.getAuditInfo().getCreatedBy().getUsername()).
			put("creationDate", folder.getAuditInfo().getCreationDate().getTime()).
			put("deleted", folder.isDeleted());
			if (folder.getAuditInfo().getModifiedBy() != null)
				json.put("modifiedBy", folder.getAuditInfo().getModifiedBy().getUsername()).
						put("modificationDate", folder.getAuditInfo().getModificationDate().getTime());
		} catch (JSONException e) {
			throw new ServletException(e);
		}
        catch (UnsupportedEncodingException e) {
            throw new ServletException(e);
        }
        return json.toString();
    }

	/**
     * Return a String with a JSON representation of the metadata
     * of the specified file. If an old file body is provided, then
     * the metadata of that particular version will be returned.
     *
	 * @param user the user that made the request
     * @param file the specified file header
     * @param oldBody the version number
     * @return the JSON-encoded file
     * @throws ServletException
	 * @throws InsufficientPermissionsException if the user does not have
	 * 			the necessary privileges to read the directory
     */
    private String renderJson(User user, FileHeader file, FileBody oldBody)
    		throws ServletException, InsufficientPermissionsException {
    	JSONObject json = new JSONObject();
    	try {
    		file=getService().expandFile(file);
    		// Need to encode file name in order to properly display it in the web client.
			json.put("name", URLEncoder.encode(file.getName(),"UTF-8")).
					put("owner", file.getOwner().getUsername()).
					put("versioned", file.isVersioned()).
					put("version", oldBody != null ? oldBody.getVersion() : file.getCurrentBody().getVersion()).
					put("readForAll", file.isReadForAll()).
					put("shared", file.getShared()).
					put("tags", renderJson(file.getFileTagsAsStrings())).
					put("path", file.getFolder().getPath()).
    				put("uri", getApiRoot() + file.getURI()).
					put("deleted", file.isDeleted());
			JSONObject j = new JSONObject();
			j.put("uri", getApiRoot() + file.getFolder().getURI()).
					put("name", URLEncoder.encode(file.getFolder().getName(),"UTF-8"));
			json.put("folder", j);
			if (oldBody != null)
				json.put("createdBy", oldBody.getAuditInfo().getCreatedBy().getUsername()).
						put("creationDate", oldBody.getAuditInfo().getCreationDate().getTime()).
						put("modifiedBy", oldBody.getAuditInfo().getModifiedBy().getUsername()).
						put("modificationDate", oldBody.getAuditInfo().getModificationDate().getTime()).
						put("content", oldBody.getMimeType()).
						put("size", oldBody.getFileSize());
			else
				json.put("createdBy", file.getAuditInfo().getCreatedBy().getUsername()).
						put("creationDate", file.getAuditInfo().getCreationDate().getTime()).
						put("modifiedBy", file.getAuditInfo().getModifiedBy().getUsername()).
						put("modificationDate", file.getAuditInfo().getModificationDate().getTime()).
						put("content", file.getCurrentBody().getMimeType()).
						put("size", file.getCurrentBody().getFileSize());
	    	Set<Permission> perms = getService().getFilePermissions(user.getId(), file.getId());
	    	json.put("permissions", renderJson(perms));
		} catch (JSONException e) {
			throw new ServletException(e);
		} catch (ObjectNotFoundException e) {
			throw new ServletException(e);
		} catch (RpcException e) {
			throw new ServletException(e);
		} catch (UnsupportedEncodingException e) {
			throw new ServletException(e);
		}

    	return json.toString();
    }

	/**
	 * Return a String with a JSON representation of the
	 * specified set of permissions.
     *
	 * @param permissions the set of permissions
	 * @return the JSON-encoded object
	 * @throws JSONException
	 * @throws UnsupportedEncodingException
	 */
	private JSONArray renderJson(Set<Permission> permissions) throws JSONException, UnsupportedEncodingException {
		JSONArray perms = new JSONArray();
		for (Permission p: permissions) {
			JSONObject permission = new JSONObject();
			permission.put("read", p.hasRead()).put("write", p.hasWrite()).put("modifyACL", p.hasModifyACL());
			if (p.getUser() != null)
				permission.put("user", p.getUser().getUsername());
			if (p.getGroup() != null) {
				Group group = p.getGroup();
				permission.put("groupUri", getApiRoot() + group.getOwner().getUsername() + PATH_GROUPS + "/" + URLEncoder.encode(group.getName(),"UTF-8"));
				permission.put("group", URLEncoder.encode(p.getGroup().getName(),"UTF-8"));
			}
			perms.put(permission);
		}
		return perms;
	}

	/**
	 * Return a String with a JSON representation of the
	 * specified collection of tags.
     *
	 * @param tags the collection of tags
	 * @return the JSON-encoded object
	 * @throws JSONException
	 * @throws UnsupportedEncodingException
	 */
	private JSONArray renderJson(Collection<String> tags) throws JSONException, UnsupportedEncodingException {
		JSONArray tagArray = new JSONArray();
		for (String t: tags)
			tagArray.put(URLEncoder.encode(t,"UTF-8"));
		return tagArray;
	}

	/**
	 * Retrieves the user who owns the destination namespace, for a
	 * copy or move request.
	 *
	 * @param req the HTTP request
	 * @return the owner of the namespace
	 */
	protected User getDestinationOwner(HttpServletRequest req) {
		return (User) req.getAttribute(DESTINATION_OWNER_ATTRIBUTE);
	}

	/**
	 * A helper inner class for updating the progress status of a file upload.
	 *
	 * @author kman
	 */
	public static class StatusProgressListener implements ProgressListener {
		private int percentLogged = 0;
		private long bytesTransferred = 0;

		private long fileSize = -100;

		private Long userId;

		private String filename;

		private ExternalAPI service;

		public StatusProgressListener(ExternalAPI aService) {
			service = aService;
		}

		/**
		 * Modify the userId.
		 *
		 * @param aUserId the userId to set
		 */
		public void setUserId(Long aUserId) {
			userId = aUserId;
		}

		/**
		 * Modify the filename.
		 *
		 * @param aFilename the filename to set
		 */
		public void setFilename(String aFilename) {
			filename = aFilename;
		}

		@Override
		public void update(long bytesRead, long contentLength, int items) {
			//monitoring per percent of bytes uploaded
			bytesTransferred = bytesRead;
			if (fileSize != contentLength)
				fileSize = contentLength;
			int percent = new Long(bytesTransferred * 100 / fileSize).intValue();
			if (percent < 5 || percent % TRACK_PROGRESS_PERCENT == 0 )
				if (percent != percentLogged){
					percentLogged = percent;
					try {
						if (userId != null && filename != null)
							service.createFileUploadProgress(userId, filename, bytesTransferred, fileSize);
					} catch (ObjectNotFoundException e) {
						// Swallow the exception since it is going to be caught
						// by previously called methods
					}
				}
		}
	}

	/**
	 * Return an InputStream to an HTML representation of the contents of this
	 * directory.
	 *
	 * @param contextPath Context path to which our internal paths are relative
	 * @param path the requested path to the resource
	 * @param folder the specified directory
	 * @param user the specified user
	 * @return an input stream with the rendered contents
	 * @throws IOException
	 * @throws ServletException
	 */
	private InputStream renderHtml(String contextPath, String path, Folder folder, User user)
		throws IOException, ServletException {
		String name = folder.getName();
		// Prepare a writer to a buffered area
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
		PrintWriter writer = new PrintWriter(osWriter);
		StringBuffer sb = new StringBuffer();
		// rewriteUrl(contextPath) is expensive. cache result for later reuse
		String rewrittenContextPath = rewriteUrl(contextPath);
		// Render the page header
		sb.append("<html>\r\n");
		sb.append("<head>\r\n");
		sb.append("<title>");
		sb.append("Index of " + name);
		sb.append("</title>\r\n");
		sb.append("<STYLE><!--");
		sb.append(GSS_CSS);
		sb.append("--></STYLE> ");
		sb.append("</head>\r\n");
		sb.append("<body>");
		sb.append("<h1>");
		sb.append("Index of " + name);

		// Render the link to our parent (if required)
		String parentDirectory = path;
		if (parentDirectory.endsWith("/"))
			parentDirectory = parentDirectory.substring(0, parentDirectory.length() - 1);
		int slash = parentDirectory.lastIndexOf('/');
		if (slash >= 0) {
			String parent = path.substring(0, slash);
			sb.append(" - <a href=\"");
			sb.append(rewrittenContextPath);
			if (parent.equals(""))
				parent = "/";
			sb.append(parent);
			if (!parent.endsWith("/"))
				sb.append("/");
			sb.append("\">");
			sb.append("<b>");
			sb.append("Up To " + parent);
			sb.append("</b>");
			sb.append("</a>");
		}

		sb.append("</h1>");
		sb.append("<HR size=\"1\" noshade=\"noshade\">");

		sb.append("<table width=\"100%\" cellspacing=\"0\"" + " cellpadding=\"5\" align=\"center\">\r\n");

		// Render the column headings
		sb.append("<tr>\r\n");
		sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
		sb.append("Name");
		sb.append("</strong></font></td>\r\n");
		sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
		sb.append("Size");
		sb.append("</strong></font></td>\r\n");
		sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
		sb.append("Last modified");
		sb.append("</strong></font></td>\r\n");
		sb.append("</tr>");
		// Render the directory entries within this directory
		boolean shade = false;
		Iterator iter = folder.getSubfolders().iterator();
		while (iter.hasNext()) {
			Folder subf = (Folder) iter.next();
			if(subf.isReadForAll() && !subf.isDeleted()){
				String resourceName = subf.getName();
				if (resourceName.equalsIgnoreCase("WEB-INF") || resourceName.equalsIgnoreCase("META-INF"))
					continue;

				sb.append("<tr");
				if (shade)
					sb.append(" bgcolor=\"#eeeeee\"");
				sb.append(">\r\n");
				shade = !shade;

				sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
				sb.append("<a href=\"");
				sb.append(rewrittenContextPath+path);
				sb.append(rewriteUrl(resourceName));
				sb.append("/");
				sb.append("\"><tt>");
				sb.append(RequestUtil.filter(resourceName));
				sb.append("/");
				sb.append("</tt></a></td>\r\n");

				sb.append("<td align=\"right\"><tt>");
				sb.append("&nbsp;");
				sb.append("</tt></td>\r\n");

				sb.append("<td align=\"right\"><tt>");
				sb.append(getLastModifiedHttp(folder.getAuditInfo()));
				sb.append("</tt></td>\r\n");

				sb.append("</tr>\r\n");

			}
		}
		List<FileHeader> files;
		try {
			files = getService().getFiles(user.getId(), folder.getId(), true);
		} catch (ObjectNotFoundException e) {
			throw new ServletException(e.getMessage());
		} catch (InsufficientPermissionsException e) {
			throw new ServletException(e.getMessage());
		} catch (RpcException e) {
			throw new ServletException(e.getMessage());
		}
		for (FileHeader file : files)
		//Display only file resources that are marked as public and are not deleted
			if(file.isReadForAll() && !file.isDeleted()){
				String resourceName = file.getName();
				if (resourceName.equalsIgnoreCase("WEB-INF") || resourceName.equalsIgnoreCase("META-INF"))
					continue;

				sb.append("<tr");
				if (shade)
					sb.append(" bgcolor=\"#eeeeee\"");
				sb.append(">\r\n");
				shade = !shade;

				sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
				sb.append("<a href=\"");
				sb.append(rewrittenContextPath + path);
				sb.append(rewriteUrl(resourceName));
				sb.append("\"><tt>");
				sb.append(RequestUtil.filter(resourceName));
				sb.append("</tt></a></td>\r\n");

				sb.append("<td align=\"right\"><tt>");
				sb.append(renderSize(file.getCurrentBody().getFileSize()));
				sb.append("</tt></td>\r\n");

				sb.append("<td align=\"right\"><tt>");
				sb.append(getLastModifiedHttp(file.getAuditInfo()));
				sb.append("</tt></td>\r\n");

				sb.append("</tr>\r\n");
			}

		// Render the page footer
		sb.append("</table>\r\n");

		sb.append("<HR size=\"1\" noshade=\"noshade\">");
		sb.append("</body>\r\n");
		sb.append("</html>\r\n");

		// Return an input stream to the underlying bytes
		writer.write(sb.toString());
		writer.flush();
		return new ByteArrayInputStream(stream.toByteArray());

	}
}
