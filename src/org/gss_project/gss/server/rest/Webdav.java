/*
 * Copyright 2007, 2008, 2009 Electronic Business Systems Ltd.
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
import org.gss_project.gss.server.domain.AuditInfo;
import org.gss_project.gss.server.domain.FileBody;
import org.gss_project.gss.server.domain.FileHeader;
import org.gss_project.gss.server.domain.Folder;
import org.gss_project.gss.server.domain.User;
import org.gss_project.gss.server.ejb.ExternalAPI;
import org.gss_project.gss.server.ejb.TransactionHelper;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The implementation of the WebDAV service.
 *
 * @author past
 */
public class Webdav extends HttpServlet {

	/**
	 * The request attribute containing the user who owns the requested
	 * namespace.
	 */
	protected static final String OWNER_ATTRIBUTE = "owner";

	/**
	 * The request attribute containing the user making the request.
	 */
	protected static final String USER_ATTRIBUTE = "user";

	/**
	 * The logger.
	 */
	private static Log logger = LogFactory.getLog(Webdav.class);

	/**
     *
     */
	protected static final String METHOD_GET = "GET";

	/**
     *
     */
	protected static final String METHOD_POST = "POST";

	/**
     *
     */
	protected static final String METHOD_PUT = "PUT";

	/**
     *
     */
	protected static final String METHOD_DELETE = "DELETE";

	/**
     *
     */
	protected static final String METHOD_HEAD = "HEAD";

	/**
     *
     */
	private static final String METHOD_OPTIONS = "OPTIONS";

	/**
     *
     */
	private static final String METHOD_PROPFIND = "PROPFIND";

	/**
     *
     */
	private static final String METHOD_PROPPATCH = "PROPPATCH";

	/**
     *
     */
	private static final String METHOD_MKCOL = "MKCOL";

	/**
     *
     */
	private static final String METHOD_COPY = "COPY";

	/**
     *
     */
	private static final String METHOD_MOVE = "MOVE";

	/**
     *
     */
	private static final String METHOD_LOCK = "LOCK";

	/**
     *
     */
	private static final String METHOD_UNLOCK = "UNLOCK";

	/**
	 * Default depth is infinite.
	 */
	static final int INFINITY = 3; // To limit tree browsing a bit

	/**
	 * PROPFIND - Specify a property mask.
	 */
	private static final int FIND_BY_PROPERTY = 0;

	/**
	 * PROPFIND - Display all properties.
	 */
	private static final int FIND_ALL_PROP = 1;

	/**
	 * PROPFIND - Return property names.
	 */
	private static final int FIND_PROPERTY_NAMES = 2;

	/**
	 * Default namespace.
	 */
	private static final String DEFAULT_NAMESPACE = "DAV:";

	/**
	 * Create a new lock.
	 */
	private static final int LOCK_CREATION = 0;

	/**
	 * Refresh lock.
	 */
	private static final int LOCK_REFRESH = 1;

	/**
	 * Default lock timeout value.
	 */
	private static final int DEFAULT_TIMEOUT = 3600;

	/**
	 * Maximum lock timeout.
	 */
	private static final int MAX_TIMEOUT = 604800;

	/**
	 * Size of file transfer buffer in bytes.
	 */
	private static final int BUFFER_SIZE = 4096;

	/**
	 * The output buffer size to use when serving resources.
	 */
	protected int output = 2048;

	/**
	 * The input buffer size to use when serving resources.
	 */
	private int input = 2048;

	/**
	 * MIME multipart separation string
	 */
	protected static final String mimeSeparation = "GSS_MIME_BOUNDARY";

	/**
	 * Simple date format for the creation date ISO representation (partial).
	 */
	private static final SimpleDateFormat creationDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	/**
	 * HTTP date format.
	 */
	private static final SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

	/**
	 * Array containing the safe characters set.
	 */
	private static URLEncoder urlEncoder;

	/**
	 * File encoding to be used when reading static files. If none is specified
	 * the platform default is used.
	 */
	private String fileEncoding = null;

	/**
	 * The style sheet for displaying the directory listings.
	 */
	private static final String GSS_CSS = "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " + "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " + "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " + "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " + "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " + "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}" + "A {color : black;}" + "A.name {color : black;}" + "HR {color : #525D76;}";

	/**
	 * Secret information used to generate reasonably secure lock ids.
	 */
	private String secret = "gss-webdav";


	/**
	 * Full range marker.
	 */
	protected static ArrayList FULL = new ArrayList();

	/**
	 * MD5 message digest provider.
	 */
	protected static MessageDigest md5Helper;

	/**
	 * The MD5 helper object for this class.
	 */
	protected static final MD5Encoder md5Encoder = new MD5Encoder();

	/**
	 * GMT timezone - all HTTP dates are on GMT
	 */
	static {
		creationDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		urlEncoder = new URLEncoder();
		urlEncoder.addSafeCharacter('-');
		urlEncoder.addSafeCharacter('_');
		urlEncoder.addSafeCharacter('.');
		urlEncoder.addSafeCharacter('*');
		urlEncoder.addSafeCharacter('/');
	}

	@Override
	public void init() throws ServletException {
		if (getServletConfig().getInitParameter("input") != null)
			input = Integer.parseInt(getServletConfig().getInitParameter("input"));

		if (getServletConfig().getInitParameter("output") != null)
			output = Integer.parseInt(getServletConfig().getInitParameter("output"));

		fileEncoding = getServletConfig().getInitParameter("fileEncoding");

		// Sanity check on the specified buffer sizes
		if (input < 256)
			input = 256;
		if (output < 256)
			output = 256;
		if (logger.isDebugEnabled())
			logger.debug("Input buffer size=" + input + ", output buffer size=" + output);

		if (getServletConfig().getInitParameter("secret") != null)
			secret = getServletConfig().getInitParameter("secret");

		// Load the MD5 helper used to calculate signatures.
		try {
			md5Helper = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new UnavailableException("No MD5");
		}
	}

	/**
	 * A helper method that retrieves a reference to the ExternalAPI bean and
	 * stores it for future use.
	 *
	 * @return an ExternalAPI instance
	 * @throws RpcException in case an error occurs
	 */
	protected ExternalAPI getService() throws RpcException {
		try {
			final Context ctx = new InitialContext();
			final Object ref = ctx.lookup(getConfiguration().getString("externalApiPath"));
			return (ExternalAPI) PortableRemoteObject.narrow(ref, ExternalAPI.class);
		} catch (final NamingException e) {
			logger.error("Unable to retrieve the ExternalAPI EJB", e);
			throw new RpcException("An error occurred while contacting the naming service");
		}
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

	@Override
	public void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
		String method = request.getMethod();

		if (logger.isDebugEnabled()) {
			String path = request.getPathInfo();
			if (path == null)
				path = request.getServletPath();
			if (path == null || path.equals(""))
				path = "/";
			logger.debug("[" + method + "] " + path);
		}

		try {
			User user = null;
			if (request.getUserPrincipal() != null) { // Let unauthenticated
														// OPTIONS go through;
														// all others will be
														// blocked by
														// authentication anyway
														// before we get here.
				user = getService().findUser(request.getUserPrincipal().getName());
				if (user == null) {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					return;
				}
			}
			request.setAttribute(USER_ATTRIBUTE, user);
			request.setAttribute(OWNER_ATTRIBUTE, user);
		} catch (RpcException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		if (method.equals(METHOD_GET))
			doGet(request, response);
		else if (method.equals(METHOD_POST))
			doPost(request, response);
		else if (method.equals(METHOD_PUT))
			doPut(request, response);
		else if (method.equals(METHOD_DELETE))
			doDelete(request, response);
		else if (method.equals(METHOD_HEAD))
			doHead(request, response);
		else if (method.equals(METHOD_PROPFIND))
			doPropfind(request, response);
		else if (method.equals(METHOD_PROPPATCH))
			doProppatch(request, response);
		else if (method.equals(METHOD_MKCOL))
			doMkcol(request, response);
		else if (method.equals(METHOD_COPY))
			doCopy(request, response);
		else if (method.equals(METHOD_MOVE))
			doMove(request, response);
		else if (method.equals(METHOD_LOCK))
			doLock(request, response);
		else if (method.equals(METHOD_UNLOCK))
			doUnlock(request, response);
		else if (method.equals(METHOD_OPTIONS))
			doOptions(request, response);
		else
			// DefaultServlet processing for TRACE, etc.
			super.service(request, response);
	}

	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.addHeader("DAV", "1,2");
		StringBuffer methodsAllowed = new StringBuffer();
		try {
			methodsAllowed = determineMethodsAllowed(req);
		} catch (RpcException e) {
			resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		resp.addHeader("Allow", methodsAllowed.toString());
		resp.addHeader("MS-Author-Via", "DAV");
	}

	/**
	 * Implement the PROPFIND method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws ServletException
	 * @throws IOException
	 */
	private void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = getRelativePath(req);
		if (path.endsWith("/") && !path.equals("/"))
			path = path.substring(0, path.length() - 1);

		if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF")) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
			return;
		}

		// Properties which are to be displayed.
		Vector<String> properties = null;
		// Propfind depth
		int depth = INFINITY;
		// Propfind type
		int type = FIND_ALL_PROP;

		String depthStr = req.getHeader("Depth");

		if (depthStr == null)
			depth = INFINITY;
		else if (depthStr.equals("0"))
			depth = 0;
		else if (depthStr.equals("1"))
			depth = 1;
		else if (depthStr.equals("infinity"))
			depth = INFINITY;

		Node propNode = null;

		if (req.getInputStream().available() > 0) {
			DocumentBuilder documentBuilder = getDocumentBuilder();

			try {
				Document document = documentBuilder.parse(new InputSource(req.getInputStream()));

				// Get the root element of the document
				Element rootElement = document.getDocumentElement();
				NodeList childList = rootElement.getChildNodes();

				for (int i = 0; i < childList.getLength(); i++) {
					Node currentNode = childList.item(i);
					switch (currentNode.getNodeType()) {
						case Node.TEXT_NODE:
							break;
						case Node.ELEMENT_NODE:
							if (currentNode.getNodeName().endsWith("prop")) {
								type = FIND_BY_PROPERTY;
								propNode = currentNode;
							}
							if (currentNode.getNodeName().endsWith("propname"))
								type = FIND_PROPERTY_NAMES;
							if (currentNode.getNodeName().endsWith("allprop"))
								type = FIND_ALL_PROP;
							break;
					}
				}
			} catch (SAXException e) {
				// Something went wrong - use the defaults.
				if (logger.isDebugEnabled())
					logger.debug(e.getMessage());
			} catch (IOException e) {
				// Something went wrong - use the defaults.
				if (logger.isDebugEnabled())
					logger.debug(e.getMessage());
			}
		}

		if (type == FIND_BY_PROPERTY) {
			properties = new Vector<String>();
			NodeList childList = propNode.getChildNodes();

			for (int i = 0; i < childList.getLength(); i++) {
				Node currentNode = childList.item(i);
				switch (currentNode.getNodeType()) {
					case Node.TEXT_NODE:
						break;
					case Node.ELEMENT_NODE:
						String nodeName = currentNode.getNodeName();
						String propertyName = null;
						if (nodeName.indexOf(':') != -1)
							propertyName = nodeName.substring(nodeName.indexOf(':') + 1);
						else
							propertyName = nodeName;
						// href is a live property which is handled differently
						properties.addElement(propertyName);
						break;
				}
			}
		}
		User user = getUser(req);
		boolean exists = true;
		Object object = null;
		try {
			object = getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
		if (!exists) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
			return;
		}
		resp.setStatus(WebdavStatus.SC_MULTI_STATUS);
		resp.setContentType("text/xml; charset=UTF-8");
		// Create multistatus object
		XMLWriter generatedXML = new XMLWriter(resp.getWriter());
		generatedXML.writeXMLHeader();
		generatedXML.writeElement(null, "D:multistatus" + generateNamespaceDeclarations(), XMLWriter.OPENING);
		if (depth == 0)
			parseProperties(req, generatedXML, path, type, properties, object);
		else {
			// The stack always contains the object of the current level
			Stack<String> stack = new Stack<String>();
			stack.push(path);

			// Stack of the objects one level below
			Stack<String> stackBelow = new Stack<String>();
			while (!stack.isEmpty() && depth >= 0) {
				String currentPath = stack.pop();
				try {
					object = getService().getResourceAtPath(user.getId(), currentPath, true);
				} catch (ObjectNotFoundException e) {
					continue;
				} catch (RpcException e) {
					resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
					return;
				}
				parseProperties(req, generatedXML, currentPath, type, properties, object);
				if (object instanceof Folder && depth > 0) {
					Folder folderLocal = (Folder) object;
					// Retrieve the subfolders.
					List subfolders = folderLocal.getSubfolders();
					Iterator iter = subfolders.iterator();
					while (iter.hasNext()) {
						Folder f = (Folder) iter.next();
						String newPath = currentPath;
						if (!newPath.endsWith("/"))
							newPath += "/";
						newPath += f.getName();
						stackBelow.push(newPath);
					}
					// Retrieve the files.
					List<FileHeader> files;
					try {
						files = getService().getFiles(user.getId(), folderLocal.getId(), true);
					} catch (ObjectNotFoundException e) {
						resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
						return;
					} catch (InsufficientPermissionsException e) {
						resp.sendError(HttpServletResponse.SC_FORBIDDEN, path);
						return;
					} catch (RpcException e) {
						resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
						return;
					}
					for (FileHeader file : files) {
						String newPath = currentPath;
						if (!newPath.endsWith("/"))
							newPath += "/";
						newPath += file.getName();
						stackBelow.push(newPath);
					}
				}
				if (stack.isEmpty()) {
					depth--;
					stack = stackBelow;
					stackBelow = new Stack<String>();
				}
				generatedXML.sendData();
			}
		}
		generatedXML.writeElement(null, "D:multistatus", XMLWriter.CLOSING);
		generatedXML.sendData();
	}

	/**
	 * PROPPATCH Method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws IOException if an error occurs while sending the response
	 */
	private void doProppatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (isLocked(req)) {
			resp.sendError(WebdavStatus.SC_LOCKED);
			return;
		}
		resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (isLocked(req)) {
			resp.sendError(WebdavStatus.SC_LOCKED);
			return;
		}
		deleteResource(req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		// Serve the requested resource, including the data content
		try {
			serveResource(req, resp, true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (isLocked(req)) {
			resp.sendError(WebdavStatus.SC_LOCKED);
			return;
		}

		final User user = getUser(req);
		String path = getRelativePath(req);
		boolean exists = true;
		Object resource = null;
		FileHeader file = null;
		try {
			resource = getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

		if (exists)
			if (resource instanceof FileHeader)
				file = (FileHeader) resource;
			else {
				resp.sendError(HttpServletResponse.SC_CONFLICT);
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
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			resourceInputStream = new FileInputStream(contentFile);
		} else
			resourceInputStream = req.getInputStream();

		try {
			Object parent = getService().getResourceAtPath(user.getId(), getParentPath(path), true);
			if (!(parent instanceof Folder)) {
				resp.sendError(HttpServletResponse.SC_CONFLICT);
				return;
			}
			final Folder folderLocal = (Folder) parent;
			final String name = getLastElement(path);
			final String mimeType = getServletContext().getMimeType(name);
        	File uploadedFile = null;
        	try {
				uploadedFile = getService().uploadFile(resourceInputStream, user.getId());
			} catch (IOException ex) {
				throw new GSSIOException(ex, false);
			}
			// FIXME: Add attributes
			FileHeader fileLocal = null;
			final FileHeader f = file;
			final File uf = uploadedFile;
			if (exists)
				fileLocal = new TransactionHelper<FileHeader>().tryExecute(new Callable<FileHeader>() {
					@Override
					public FileHeader call() throws Exception {
						return getService().updateFileContents(user.getId(), f.getId(), mimeType, uf.length(), uf.getAbsolutePath());
					}
				});
			else
				fileLocal = new TransactionHelper<FileHeader>().tryExecute(new Callable<FileHeader>() {
					@Override
					public FileHeader call() throws Exception {
						return getService().createFile(user.getId(), folderLocal.getId(), name, mimeType, uf.length(), uf.getAbsolutePath());
					}
				});
			updateAccounting(user, new Date(), fileLocal.getCurrentBody().getFileSize());
		} catch (ObjectNotFoundException e) {
			result = false;
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		} catch (QuotaExceededException e) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		} catch (GSSIOException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		} catch (DuplicateNameException e) {
			resp.sendError(HttpServletResponse.SC_CONFLICT);
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

	@Override
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		// Serve the requested resource, without the data content
		try {
			serveResource(req, resp, false);
		} catch (ObjectNotFoundException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		} catch (InsufficientPermissionsException e) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
	}

	/**
	 * The UNLOCK method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws IOException if an error occurs while sending the response
	 */
	private void doUnlock(@SuppressWarnings("unused") HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setStatus(WebdavStatus.SC_NO_CONTENT);
	}

	/**
	 * The LOCK method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws IOException if an error occurs while sending the response
	 * @throws ServletException
	 */
	private void doLock(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		LockInfo lock = new LockInfo();
		// Parsing lock request

		// Parsing depth header
		String depthStr = req.getHeader("Depth");
		if (depthStr == null)
			lock.depth = INFINITY;
		else if (depthStr.equals("0"))
			lock.depth = 0;
		else
			lock.depth = INFINITY;

		// Parsing timeout header
		int lockDuration = DEFAULT_TIMEOUT;
		String lockDurationStr = req.getHeader("Timeout");
		if (lockDurationStr == null)
			lockDuration = DEFAULT_TIMEOUT;
		else {
			int commaPos = lockDurationStr.indexOf(",");
			// If multiple timeouts, just use the first
			if (commaPos != -1)
				lockDurationStr = lockDurationStr.substring(0, commaPos);
			if (lockDurationStr.startsWith("Second-"))
				lockDuration = new Integer(lockDurationStr.substring(7)).intValue();
			else if (lockDurationStr.equalsIgnoreCase("infinity"))
				lockDuration = MAX_TIMEOUT;
			else
				try {
					lockDuration = new Integer(lockDurationStr).intValue();
				} catch (NumberFormatException e) {
					lockDuration = MAX_TIMEOUT;
				}
			if (lockDuration == 0)
				lockDuration = DEFAULT_TIMEOUT;
			if (lockDuration > MAX_TIMEOUT)
				lockDuration = MAX_TIMEOUT;
		}
		lock.expiresAt = System.currentTimeMillis() + lockDuration * 1000;

		int lockRequestType = LOCK_CREATION;
		Node lockInfoNode = null;
		DocumentBuilder documentBuilder = getDocumentBuilder();

		try {
			Document document = documentBuilder.parse(new InputSource(req.getInputStream()));
			// Get the root element of the document
			Element rootElement = document.getDocumentElement();
			lockInfoNode = rootElement;
		} catch (IOException e) {
			lockRequestType = LOCK_REFRESH;
		} catch (SAXException e) {
			lockRequestType = LOCK_REFRESH;
		}

		if (lockInfoNode != null) {
			// Reading lock information
			NodeList childList = lockInfoNode.getChildNodes();
			StringWriter strWriter = null;
			DOMWriter domWriter = null;

			Node lockScopeNode = null;
			Node lockTypeNode = null;
			Node lockOwnerNode = null;

			for (int i = 0; i < childList.getLength(); i++) {
				Node currentNode = childList.item(i);
				switch (currentNode.getNodeType()) {
					case Node.TEXT_NODE:
						break;
					case Node.ELEMENT_NODE:
						String nodeName = currentNode.getNodeName();
						if (nodeName.endsWith("lockscope"))
							lockScopeNode = currentNode;
						if (nodeName.endsWith("locktype"))
							lockTypeNode = currentNode;
						if (nodeName.endsWith("owner"))
							lockOwnerNode = currentNode;
						break;
				}
			}

			if (lockScopeNode != null) {
				childList = lockScopeNode.getChildNodes();
				for (int i = 0; i < childList.getLength(); i++) {
					Node currentNode = childList.item(i);
					switch (currentNode.getNodeType()) {
						case Node.TEXT_NODE:
							break;
						case Node.ELEMENT_NODE:
							String tempScope = currentNode.getNodeName();
							if (tempScope.indexOf(':') != -1)
								lock.scope = tempScope.substring(tempScope.indexOf(':') + 1);
							else
								lock.scope = tempScope;
							break;
					}
				}
				if (lock.scope == null)
					// Bad request
					resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
			} else
				// Bad request
				resp.setStatus(WebdavStatus.SC_BAD_REQUEST);

			if (lockTypeNode != null) {
				childList = lockTypeNode.getChildNodes();
				for (int i = 0; i < childList.getLength(); i++) {
					Node currentNode = childList.item(i);
					switch (currentNode.getNodeType()) {
						case Node.TEXT_NODE:
							break;
						case Node.ELEMENT_NODE:
							String tempType = currentNode.getNodeName();
							if (tempType.indexOf(':') != -1)
								lock.type = tempType.substring(tempType.indexOf(':') + 1);
							else
								lock.type = tempType;
							break;
					}
				}

				if (lock.type == null)
					// Bad request
					resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
			} else
				// Bad request
				resp.setStatus(WebdavStatus.SC_BAD_REQUEST);

			if (lockOwnerNode != null) {
				childList = lockOwnerNode.getChildNodes();
				for (int i = 0; i < childList.getLength(); i++) {
					Node currentNode = childList.item(i);
					switch (currentNode.getNodeType()) {
						case Node.TEXT_NODE:
							lock.owner += currentNode.getNodeValue();
							break;
						case Node.ELEMENT_NODE:
							strWriter = new StringWriter();
							domWriter = new DOMWriter(strWriter, true);
							domWriter.setQualifiedNames(false);
							domWriter.print(currentNode);
							lock.owner += strWriter.toString();
							break;
					}
				}

				if (lock.owner == null)
					// Bad request
					resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
			} else
				lock.owner = new String();
		}

		String path = getRelativePath(req);
		lock.path = path;
		User user = getUser(req);
		boolean exists = true;
		Object object = null;
		try {
			object = getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

		if (lockRequestType == LOCK_CREATION) {
			// Generating lock id
			String lockTokenStr = req.getServletPath() + "-" + lock.type + "-" + lock.scope + "-" + req.getUserPrincipal() + "-" + lock.depth + "-" + lock.owner + "-" + lock.tokens + "-" + lock.expiresAt + "-" + System.currentTimeMillis() + "-" + secret;
			String lockToken = md5Encoder.encode(md5Helper.digest(lockTokenStr.getBytes()));

			if (exists && object instanceof Folder && lock.depth == INFINITY)
				// Locking a collection (and all its member resources)
				lock.tokens.addElement(lockToken);
			else {
				// Locking a single resource
				lock.tokens.addElement(lockToken);
				// Add the Lock-Token header as by RFC 2518 8.10.1
				// - only do this for newly created locks
				resp.addHeader("Lock-Token", "<opaquelocktoken:" + lockToken + ">");

			}
		}

		if (lockRequestType == LOCK_REFRESH) {

		}

		// Set the status, then generate the XML response containing
		// the lock information.
		XMLWriter generatedXML = new XMLWriter();
		generatedXML.writeXMLHeader();
		generatedXML.writeElement(null, "prop" + generateNamespaceDeclarations(), XMLWriter.OPENING);
		generatedXML.writeElement(null, "lockdiscovery", XMLWriter.OPENING);
		lock.toXML(generatedXML);
		generatedXML.writeElement(null, "lockdiscovery", XMLWriter.CLOSING);
		generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);

		resp.setStatus(WebdavStatus.SC_OK);
		resp.setContentType("text/xml; charset=UTF-8");
		Writer writer = resp.getWriter();
		writer.write(generatedXML.toString());
		writer.close();
	}

	/**
	 * The MOVE method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws IOException if an error occurs while sending the response
	 * @throws ServletException
	 */
	private void doMove(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		if (isLocked(req)) {
			resp.sendError(WebdavStatus.SC_LOCKED);
			return;
		}

		String path = getRelativePath(req);

		if (copyResource(req, resp))
			deleteResource(path, req, resp, false);
	}

	/**
	 * The COPY method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws IOException if an error occurs while sending the response
	 * @throws ServletException
	 */
	private void doCopy(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		copyResource(req, resp);
	}

	/**
	 * The MKCOL method.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @throws IOException if an error occurs while sending the response
	 * @throws ServletException
	 */
	private void doMkcol(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		if (isLocked(req)) {
			resp.sendError(WebdavStatus.SC_LOCKED);
			return;
		}
		final String path = getRelativePath(req);
		if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF")) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
			return;
		}

		final User user = getUser(req);
		boolean exists = true;
		try {
			getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

		// Can't create a collection if a resource already exists at the given
		// path.
		if (exists) {
			// Get allowed methods.
			StringBuffer methodsAllowed;
			try {
				methodsAllowed = determineMethodsAllowed(req);
			} catch (RpcException e) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
				return;
			}
			resp.addHeader("Allow", methodsAllowed.toString());
			resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
			return;
		}

		if (req.getInputStream().available() > 0) {
			DocumentBuilder documentBuilder = getDocumentBuilder();
			try {
				@SuppressWarnings("unused")
				Document document = documentBuilder.parse(new InputSource(req.getInputStream()));
				// TODO : Process this request body
				resp.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
				return;
			} catch (SAXException saxe) {
				// Parse error - assume invalid content
				resp.sendError(WebdavStatus.SC_BAD_REQUEST);
				return;
			}
		}

		Object parent;
		try {
			parent = getService().getResourceAtPath(user.getId(), getParentPath(path), true);
		} catch (ObjectNotFoundException e) {
			resp.sendError(WebdavStatus.SC_CONFLICT, WebdavStatus.getStatusText(WebdavStatus.SC_CONFLICT));
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
		try {
			if (parent instanceof Folder) {
				final Folder folderLocal = (Folder) parent;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().createFolder(user.getId(), folderLocal.getId(), getLastElement(path));
						return null;
					}
				});
			} else {
				resp.sendError(WebdavStatus.SC_FORBIDDEN, WebdavStatus.getStatusText(WebdavStatus.SC_FORBIDDEN));
				return;
			}
		} catch (DuplicateNameException e) {
			// XXX If the existing name is a folder we should be returning
			// SC_METHOD_NOT_ALLOWED, or even better, just do the createFolder
			// without checking first and then deal with the exceptions.
			resp.sendError(WebdavStatus.SC_FORBIDDEN, WebdavStatus.getStatusText(WebdavStatus.SC_FORBIDDEN));
			return;
		} catch (InsufficientPermissionsException e) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN, WebdavStatus.getStatusText(WebdavStatus.SC_FORBIDDEN));
			return;
		} catch (ObjectNotFoundException e) {
			resp.sendError(WebdavStatus.SC_CONFLICT, WebdavStatus.getStatusText(WebdavStatus.SC_CONFLICT));
			return;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		} catch (Exception e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}
		resp.setStatus(WebdavStatus.SC_CREATED);
	}

	/**
	 * For a provided path, remove the last element and return the rest, that is
	 * the path of the parent folder.
	 *
	 * @param path the specified path
	 * @return the path of the parent folder
	 * @throws ObjectNotFoundException if the provided string contains no path
	 *             delimiters
	 */
	protected String getParentPath(String path) throws ObjectNotFoundException {
		int lastDelimiter = path.lastIndexOf('/');
		if (lastDelimiter == 0)
			return "/";
		if (lastDelimiter == -1)
			// No path found.
			throw new ObjectNotFoundException("There is no parent in the path: " + path);
		else if (lastDelimiter < path.length() - 1)
			// Return the part before the delimiter.
			return path.substring(0, lastDelimiter);
		else {
			// Remove the trailing delimiter and then recurse.
			String strippedTrail = path.substring(0, lastDelimiter);
			return getParentPath(strippedTrail);
		}
	}

	/**
	 * Get the last element in a path that denotes the file or folder name.
	 *
	 * @param path the provided path
	 * @return the last element in the path
	 */
	protected String getLastElement(String path) {
		int lastDelimiter = path.lastIndexOf('/');
		if (lastDelimiter == -1)
			// No path found.
			return path;
		else if (lastDelimiter < path.length() - 1)
			// Return the part after the delimiter.
			return path.substring(lastDelimiter + 1);
		else {
			// Remove the trailing delimiter and then recurse.
			String strippedTrail = path.substring(0, lastDelimiter);
			return getLastElement(strippedTrail);
		}
	}

	/**
	 * Only use the PathInfo for determining the requested path. If the
	 * ServletPath is non-null, it will be because the WebDAV servlet has been
	 * mapped to a URL other than /* to configure editing at different URL than
	 * normal viewing.
	 *
	 * @param request the servlet request we are processing
	 * @return the relative path
	 * @throws UnsupportedEncodingException
	 */
	protected String getRelativePath(HttpServletRequest request) {
		// Remove the servlet path from the request URI.
		String p = request.getRequestURI();
		String servletPath = request.getContextPath() + request.getServletPath();
		String result = p.substring(servletPath.length());
		try {
			result = URLDecoder.decode(result, "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}
		if (result == null || result.equals(""))
			result = "/";
		return result;

	}

	/**
	 * Return JAXP document builder instance.
	 *
	 * @return the DocumentBuilder
	 * @throws ServletException
	 */
	private DocumentBuilder getDocumentBuilder() throws ServletException {
		DocumentBuilder documentBuilder = null;
		DocumentBuilderFactory documentBuilderFactory = null;
		try {
			documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(true);
			documentBuilderFactory.setExpandEntityReferences(false);
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			documentBuilder.setEntityResolver(new WebdavResolver(getServletContext()));
		} catch (ParserConfigurationException e) {
			throw new ServletException("Error while creating a document builder");
		}
		return documentBuilder;
	}

	/**
	 * Generate the namespace declarations.
	 *
	 * @return the namespace declarations
	 */
	private String generateNamespaceDeclarations() {
		return " xmlns:D=\"" + DEFAULT_NAMESPACE + "\"";
	}

	/**
	 * Propfind helper method. Dispays the properties of a lock-null resource.
	 *
	 * @param req the HTTP request
	 * @param resources Resources object associated with this context
	 * @param generatedXML XML response to the Propfind request
	 * @param path Path of the current resource
	 * @param type Propfind type
	 * @param propertiesVector If the propfind type is find properties by name,
	 *            then this Vector contains those properties
	 */
	@SuppressWarnings("unused")
	private void parseLockNullProperties(HttpServletRequest req, XMLWriter generatedXML, String path, int type, Vector propertiesVector) {
		return;
	}

	/**
	 * Propfind helper method.
	 *
	 * @param req The servlet request
	 * @param resources Resources object associated with this context
	 * @param generatedXML XML response to the Propfind request
	 * @param path Path of the current resource
	 * @param type Propfind type
	 * @param propertiesVector If the propfind type is find properties by name,
	 *            then this Vector contains those properties
	 * @param resource the resource object
	 */
	private void parseProperties(HttpServletRequest req, XMLWriter generatedXML, String path, int type, Vector<String> propertiesVector, Object resource) {

		// Exclude any resource in the /WEB-INF and /META-INF subdirectories
		// (the "toUpperCase()" avoids problems on Windows systems)
		if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF"))
			return;

		Folder folderLocal = null;
		FileHeader fileLocal = null;
		if (resource instanceof Folder)
			folderLocal = (Folder) resource;
		else
			fileLocal = (FileHeader) resource;
		// Retrieve the creation date.
		long creation = 0;
		if (folderLocal != null)
			creation = folderLocal.getAuditInfo().getCreationDate().getTime();
		else
			creation = fileLocal.getAuditInfo().getCreationDate().getTime();
		// Retrieve the modification date.
		long modification = 0;
		if (folderLocal != null)
			modification = folderLocal.getAuditInfo().getCreationDate().getTime();
		else
			modification = fileLocal.getAuditInfo().getCreationDate().getTime();

		generatedXML.writeElement(null, "D:response", XMLWriter.OPENING);
		String status = new String("HTTP/1.1 " + WebdavStatus.SC_OK + " " + WebdavStatus.getStatusText(WebdavStatus.SC_OK));

		// Generating href element
		generatedXML.writeElement(null, "D:href", XMLWriter.OPENING);

		String href = req.getContextPath() + req.getServletPath();
		if (href.endsWith("/") && path.startsWith("/"))
			href += path.substring(1);
		else
			href += path;
		if (folderLocal != null && !href.endsWith("/"))
			href += "/";

		generatedXML.writeText(rewriteUrl(href));

		generatedXML.writeElement(null, "D:href", XMLWriter.CLOSING);

		String resourceName = path;
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash != -1)
			resourceName = resourceName.substring(lastSlash + 1);
		if (resourceName.isEmpty())
			resourceName = "/";

		switch (type) {

			case FIND_ALL_PROP:

				generatedXML.writeElement(null, "D:propstat", XMLWriter.OPENING);
				generatedXML.writeElement(null, "D:prop", XMLWriter.OPENING);

				generatedXML.writeProperty(null, "D:creationdate", getISOCreationDate(creation));
				generatedXML.writeElement(null, "D:displayname", XMLWriter.OPENING);
				generatedXML.writeData(resourceName);
				generatedXML.writeElement(null, "D:displayname", XMLWriter.CLOSING);
				if (fileLocal != null) {
					generatedXML.writeProperty(null, "D:getlastmodified", FastHttpDateFormat.formatDate(modification, null));
					generatedXML.writeProperty(null, "D:getcontentlength", String.valueOf(fileLocal.getCurrentBody().getFileSize()));
					String contentType = fileLocal.getCurrentBody().getMimeType();
					if (contentType != null)
						generatedXML.writeProperty(null, "D:getcontenttype", contentType);
					generatedXML.writeProperty(null, "D:getetag", getETag(fileLocal, null));
					generatedXML.writeElement(null, "D:resourcetype", XMLWriter.NO_CONTENT);
				} else {
					generatedXML.writeElement(null, "D:resourcetype", XMLWriter.OPENING);
					generatedXML.writeElement(null, "D:collection", XMLWriter.NO_CONTENT);
					generatedXML.writeElement(null, "D:resourcetype", XMLWriter.CLOSING);
				}

				generatedXML.writeProperty(null, "D:source", "");

				String supportedLocks = "<D:lockentry>" + "<D:lockscope><D:exclusive/></D:lockscope>" + "<D:locktype><D:write/></D:locktype>" + "</D:lockentry>" + "<D:lockentry>" + "<D:lockscope><D:shared/></D:lockscope>" + "<D:locktype><D:write/></D:locktype>" + "</D:lockentry>";
				generatedXML.writeElement(null, "D:supportedlock", XMLWriter.OPENING);
				generatedXML.writeText(supportedLocks);
				generatedXML.writeElement(null, "D:supportedlock", XMLWriter.CLOSING);

				generateLockDiscovery(path, generatedXML);

				generatedXML.writeElement(null, "D:prop", XMLWriter.CLOSING);
				generatedXML.writeElement(null, "D:status", XMLWriter.OPENING);
				generatedXML.writeText(status);
				generatedXML.writeElement(null, "D:status", XMLWriter.CLOSING);
				generatedXML.writeElement(null, "D:propstat", XMLWriter.CLOSING);

				break;

			case FIND_PROPERTY_NAMES:

				generatedXML.writeElement(null, "D:propstat", XMLWriter.OPENING);
				generatedXML.writeElement(null, "D:prop", XMLWriter.OPENING);

				generatedXML.writeElement(null, "D:creationdate", XMLWriter.NO_CONTENT);
				generatedXML.writeElement(null, "D:displayname", XMLWriter.NO_CONTENT);
				if (fileLocal != null) {
					generatedXML.writeElement(null, "D:getcontentlanguage", XMLWriter.NO_CONTENT);
					generatedXML.writeElement(null, "D:getcontentlength", XMLWriter.NO_CONTENT);
					generatedXML.writeElement(null, "D:getcontenttype", XMLWriter.NO_CONTENT);
					generatedXML.writeElement(null, "D:getetag", XMLWriter.NO_CONTENT);
					generatedXML.writeElement(null, "D:getlastmodified", XMLWriter.NO_CONTENT);
				}
				generatedXML.writeElement(null, "D:resourcetype", XMLWriter.NO_CONTENT);
				generatedXML.writeElement(null, "D:source", XMLWriter.NO_CONTENT);
				generatedXML.writeElement(null, "D:lockdiscovery", XMLWriter.NO_CONTENT);

				generatedXML.writeElement(null, "D:prop", XMLWriter.CLOSING);
				generatedXML.writeElement(null, "D:status", XMLWriter.OPENING);
				generatedXML.writeText(status);
				generatedXML.writeElement(null, "D:status", XMLWriter.CLOSING);
				generatedXML.writeElement(null, "D:propstat", XMLWriter.CLOSING);

				break;

			case FIND_BY_PROPERTY:

				Vector<String> propertiesNotFound = new Vector<String>();

				// Parse the list of properties

				generatedXML.writeElement(null, "D:propstat", XMLWriter.OPENING);
				generatedXML.writeElement(null, "D:prop", XMLWriter.OPENING);

				Enumeration<String> properties = propertiesVector.elements();

				while (properties.hasMoreElements()) {

					String property = properties.nextElement();

					if (property.equals("D:creationdate"))
						generatedXML.writeProperty(null, "D:creationdate", getISOCreationDate(creation));
					else if (property.equals("D:displayname")) {
						generatedXML.writeElement(null, "D:displayname", XMLWriter.OPENING);
						generatedXML.writeData(resourceName);
						generatedXML.writeElement(null, "D:displayname", XMLWriter.CLOSING);
					} else if (property.equals("D:getcontentlanguage")) {
						if (folderLocal != null)
							propertiesNotFound.addElement(property);
						else
							generatedXML.writeElement(null, "D:getcontentlanguage", XMLWriter.NO_CONTENT);
					} else if (property.equals("D:getcontentlength")) {
						if (folderLocal != null)
							propertiesNotFound.addElement(property);
						else
							generatedXML.writeProperty(null, "D:getcontentlength", String.valueOf(fileLocal.getCurrentBody().getFileSize()));
					} else if (property.equals("D:getcontenttype")) {
						if (folderLocal != null)
							propertiesNotFound.addElement(property);
						else
							// XXX Once we properly store the MIME type in the
							// file,
							// retrieve it from there.
							generatedXML.writeProperty(null, "D:getcontenttype", getServletContext().getMimeType(fileLocal.getName()));
					} else if (property.equals("D:getetag")) {
						if (folderLocal != null)
							propertiesNotFound.addElement(property);
						else
							generatedXML.writeProperty(null, "D:getetag", getETag(fileLocal, null));
					} else if (property.equals("D:getlastmodified")) {
						if (folderLocal != null)
							propertiesNotFound.addElement(property);
						else
							generatedXML.writeProperty(null, "D:getlastmodified", FastHttpDateFormat.formatDate(modification, null));
					} else if (property.equals("D:resourcetype")) {
						if (folderLocal != null) {
							generatedXML.writeElement(null, "D:resourcetype", XMLWriter.OPENING);
							generatedXML.writeElement(null, "D:collection", XMLWriter.NO_CONTENT);
							generatedXML.writeElement(null, "D:resourcetype", XMLWriter.CLOSING);
						} else
							generatedXML.writeElement(null, "D:resourcetype", XMLWriter.NO_CONTENT);
					} else if (property.equals("D:source"))
						generatedXML.writeProperty(null, "D:source", "");
					else if (property.equals("D:supportedlock")) {
						supportedLocks = "<D:lockentry>" + "<D:lockscope><D:exclusive/></D:lockscope>" + "<D:locktype><D:write/></D:locktype>" + "</D:lockentry>" + "<D:lockentry>" + "<D:lockscope><D:shared/></D:lockscope>" + "<D:locktype><D:write/></D:locktype>" + "</D:lockentry>";
						generatedXML.writeElement(null, "D:supportedlock", XMLWriter.OPENING);
						generatedXML.writeText(supportedLocks);
						generatedXML.writeElement(null, "D:supportedlock", XMLWriter.CLOSING);
					} else if (property.equals("D:lockdiscovery")) {
						if (!generateLockDiscovery(path, generatedXML))
							propertiesNotFound.addElement(property);
					} else
						propertiesNotFound.addElement(property);
				}

				generatedXML.writeElement(null, "D:prop", XMLWriter.CLOSING);
				generatedXML.writeElement(null, "D:status", XMLWriter.OPENING);
				generatedXML.writeText(status);
				generatedXML.writeElement(null, "D:status", XMLWriter.CLOSING);
				generatedXML.writeElement(null, "D:propstat", XMLWriter.CLOSING);

				Enumeration propertiesNotFoundList = propertiesNotFound.elements();

				if (propertiesNotFoundList.hasMoreElements()) {

					status = new String("HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND + " " + WebdavStatus.getStatusText(WebdavStatus.SC_NOT_FOUND));

					generatedXML.writeElement(null, "D:propstat", XMLWriter.OPENING);
					generatedXML.writeElement(null, "D:prop", XMLWriter.OPENING);

					while (propertiesNotFoundList.hasMoreElements())
						generatedXML.writeElement(null, (String) propertiesNotFoundList.nextElement(), XMLWriter.NO_CONTENT);
					generatedXML.writeElement(null, "D:prop", XMLWriter.CLOSING);
					generatedXML.writeElement(null, "D:status", XMLWriter.OPENING);
					generatedXML.writeText(status);
					generatedXML.writeElement(null, "D:status", XMLWriter.CLOSING);
					generatedXML.writeElement(null, "D:propstat", XMLWriter.CLOSING);
				}

				break;

		}

		generatedXML.writeElement(null, "D:response", XMLWriter.CLOSING);

	}

	/**
	 * Get the ETag associated with a file.
	 *
	 * @param file the FileHeader object for this file
	 * @param oldBody the old version of the file, if requested
	 * @return a string containing the ETag
	 */
	protected String getETag(FileHeader file, FileBody oldBody) {
		if (oldBody == null)
			return "\"" + file.getCurrentBody().getFileSize() + "-" + file.getAuditInfo().getModificationDate().getTime() + "\"";
		return "\"" + oldBody.getFileSize() + "-" + oldBody.getAuditInfo().getModificationDate().getTime() + "\"";
	}

	/**
	 * URL rewriter.
	 *
	 * @param path Path which has to be rewritten
	 * @return the rewritten URL
	 */
	protected String rewriteUrl(String path) {
		return urlEncoder.encode(path);
	}

	/**
	 * Print the lock discovery information associated with a path.
	 *
	 * @param path Path
	 * @param generatedXML XML data to which the locks info will be appended
	 * @return true if at least one lock was displayed
	 */
	@SuppressWarnings("unused")
	private boolean generateLockDiscovery(String path,  XMLWriter generatedXML) {
			return false;
	}

	/**
	 * Get creation date in ISO format.
	 *
	 * @param creationDate
	 * @return the formatted date
	 */
	private String getISOCreationDate(long creationDate) {
		String dateValue = null;
		synchronized (creationDateFormat) {
			dateValue = creationDateFormat.format(new Date(creationDate));
		}
		StringBuffer creationDateValue = new StringBuffer(dateValue);
		/*
		int offset = Calendar.getInstance().getTimeZone().getRawOffset()
		    / 3600000; // FIXME ?
		if (offset < 0) {
		    creationDateValue.append("-");
		    offset = -offset;
		} else if (offset > 0) {
		    creationDateValue.append("+");
		}
		if (offset != 0) {
		    if (offset < 10)
		        creationDateValue.append("0");
		    creationDateValue.append(offset + ":00");
		} else {
		    creationDateValue.append("Z");
		}
		 */
		return creationDateValue.toString();
	}

	/**
	 * Determines the methods normally allowed for the resource.
	 *
	 * @param req the HTTP request
	 * @return a list of the allowed methods
	 * @throws RpcException if there is an error while communicating with the
	 *             backend
	 */
	private StringBuffer determineMethodsAllowed(HttpServletRequest req) throws RpcException {
		StringBuffer methodsAllowed = new StringBuffer();
		boolean exists = true;
		Object object = null;
		User user = getUser(req);
		String path = getRelativePath(req);
		if (user == null && "/".equals(path))
			// Special case: OPTIONS request before authentication
			return new StringBuffer("OPTIONS, GET, HEAD, POST, DELETE, TRACE, PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, PUT, MKCOL");
		try {
			object = getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		}

		if (!exists) {
			methodsAllowed.append("OPTIONS, MKCOL, PUT, LOCK");
			return methodsAllowed;
		}

		methodsAllowed.append("OPTIONS, GET, HEAD, POST, DELETE, TRACE");
		methodsAllowed.append(", PROPPATCH, COPY, MOVE, LOCK, UNLOCK");
		methodsAllowed.append(", PROPFIND");

		if (!(object instanceof Folder))
			methodsAllowed.append(", PUT");

		return methodsAllowed;
	}

	/**
	 * Check to see if a resource is currently write locked. The method will
	 * look at the "If" header to make sure the client has given the appropriate
	 * lock tokens.
	 *
	 * @param req the HTTP request
	 * @return boolean true if the resource is locked (and no appropriate lock
	 *         token has been found for at least one of the non-shared locks
	 *         which are present on the resource).
	 */
	private boolean isLocked(@SuppressWarnings("unused") HttpServletRequest req) {
		return false;
	}

	/**
	 * Check to see if a resource is currently write locked.
	 *
	 * @param path Path of the resource
	 * @param ifHeader "If" HTTP header which was included in the request
	 * @return boolean true if the resource is locked (and no appropriate lock
	 *         token has been found for at least one of the non-shared locks
	 *         which are present on the resource).
	 */
	private boolean isLocked(@SuppressWarnings("unused") String path, @SuppressWarnings("unused") String ifHeader) {
		return false;
	}

	/**
	 * Parse the content-range header.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @return Range
	 * @throws IOException
	 */
	protected Range parseContentRange(HttpServletRequest request, HttpServletResponse response) throws IOException {

		// Retrieving the content-range header (if any is specified
		String rangeHeader = request.getHeader("Content-Range");

		if (rangeHeader == null)
			return null;

		// bytes is the only range unit supported
		if (!rangeHeader.startsWith("bytes")) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}

		rangeHeader = rangeHeader.substring(6).trim();

		int dashPos = rangeHeader.indexOf('-');
		int slashPos = rangeHeader.indexOf('/');

		if (dashPos == -1) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}

		if (slashPos == -1) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}

		Range range = new Range();

		try {
			range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
			range.end = Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
			range.length = Long.parseLong(rangeHeader.substring(slashPos + 1, rangeHeader.length()));
		} catch (NumberFormatException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}

		if (!range.validate()) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}

		return range;

	}

	/**
	 * Handle a partial PUT. New content specified in request is appended to
	 * existing content in oldRevisionContent (if present). This code does not
	 * support simultaneous partial updates to the same resource.
	 *
	 * @param req
	 * @param range
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected File executePartialPut(HttpServletRequest req, Range range, String path) throws IOException, RpcException, ObjectNotFoundException, InsufficientPermissionsException {
		// Append data specified in ranges to existing content for this
		// resource - create a temporary file on the local file system to
		// perform this operation.
		File tempDir = (File) getServletContext().getAttribute("javax.servlet.context.tempdir");
		// Convert all '/' characters to '.' in resourcePath
		String convertedResourcePath = path.replace('/', '.');
		File contentFile = new File(tempDir, convertedResourcePath);
		if (contentFile.createNewFile())
			// Clean up contentFile when Tomcat is terminated.
			contentFile.deleteOnExit();

		RandomAccessFile randAccessContentFile = new RandomAccessFile(contentFile, "rw");

		User user = getUser(req);
		User owner = getOwner(req);
		FileHeader oldResource = null;
		try {
			Object obj = getService().getResourceAtPath(owner.getId(), path, true);
			if (obj instanceof FileHeader)
				oldResource = (FileHeader) obj;
		} catch (ObjectNotFoundException e) {
			// Do nothing.
		}

		// Copy data in oldRevisionContent to contentFile
		if (oldResource != null) {
			InputStream contents = getService().getFileContents(user.getId(), oldResource.getId());
			BufferedInputStream bufOldRevStream = new BufferedInputStream(contents, BUFFER_SIZE);

			int numBytesRead;
			byte[] copyBuffer = new byte[BUFFER_SIZE];
			while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1)
				randAccessContentFile.write(copyBuffer, 0, numBytesRead);

			bufOldRevStream.close();
		}

		randAccessContentFile.setLength(range.length);

		// Append data in request input stream to contentFile
		randAccessContentFile.seek(range.start);
		int numBytesRead;
		byte[] transferBuffer = new byte[BUFFER_SIZE];
		BufferedInputStream requestBufInStream = new BufferedInputStream(req.getInputStream(), BUFFER_SIZE);
		while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1)
			randAccessContentFile.write(transferBuffer, 0, numBytesRead);
		randAccessContentFile.close();
		requestBufInStream.close();

		return contentFile;

	}

	/**
	 * Serve the specified resource, optionally including the data content.
	 *
	 * @param req The servlet request we are processing
	 * @param resp The servlet response we are creating
	 * @param content Should the content be included?
	 * @exception IOException if an input/output error occurs
	 * @exception ServletException if a servlet-specified error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void serveResource(HttpServletRequest req, HttpServletResponse resp, boolean content) throws IOException, ServletException, ObjectNotFoundException, InsufficientPermissionsException, RpcException {

		// Identify the requested resource path
		String path = getRelativePath(req);
		if (logger.isDebugEnabled())
			if (content)
				logger.debug("Serving resource '" + path + "' headers and data");
			else
				logger.debug("Serving resource '" + path + "' headers only");

		User user = getUser(req);
		boolean exists = true;
		Object resource = null;
		FileHeader file = null;
		Folder folder = null;
		try {
			resource = getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, path);
			return;
		}

		if (!exists) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
			return;
		}

		if (resource instanceof Folder)
			folder = (Folder) resource;
		else
			file = (FileHeader) resource;

		// If the resource is not a collection, and the resource path
		// ends with "/" or "\", return NOT FOUND
		if (folder == null)
			if (path.endsWith("/") || path.endsWith("\\")) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
				return;
			}

		// Check if the conditions specified in the optional If headers are
		// satisfied.
		if (folder == null)
			// Checking If headers
			if (!checkIfHeaders(req, resp, file, null))
				return;

		// Find content type.
		String contentType = null;
		if (file != null) {
			contentType = file.getCurrentBody().getMimeType();
			if (contentType == null) {
				contentType = getServletContext().getMimeType(file.getName());
				file.getCurrentBody().setMimeType(contentType);
			}
		} else
			contentType = "text/html;charset=UTF-8";

		ArrayList ranges = null;
		long contentLength = -1L;

		if (file != null) {
			// Accept ranges header
			resp.setHeader("Accept-Ranges", "bytes");
			// Parse range specifier
			ranges = parseRange(req, resp, file, null);
			// ETag header
			resp.setHeader("ETag", getETag(file, null));
			// Last-Modified header
			resp.setHeader("Last-Modified", getLastModifiedHttp(file.getAuditInfo()));
			// Get content length
			contentLength = file.getCurrentBody().getFileSize();
			// Special case for zero length files, which would cause a
			// (silent) ISE when setting the output buffer size
			if (contentLength == 0L)
				content = false;
		}

		ServletOutputStream ostream = null;
		PrintWriter writer = null;

		if (content)
			try {
				ostream = resp.getOutputStream();
			} catch (IllegalStateException e) {
				// If it fails, we try to get a Writer instead if we're
				// trying to serve a text file
				if (contentType == null || contentType.startsWith("text") || contentType.endsWith("xml"))
					writer = resp.getWriter();
				else
					throw e;
			}

		if (folder != null || (ranges == null || ranges.isEmpty()) && req.getHeader("Range") == null || ranges == FULL) {
			// Set the appropriate output headers
			if (contentType != null) {
				if (logger.isDebugEnabled())
					logger.debug("DefaultServlet.serveFile:  contentType='" + contentType + "'");
				resp.setContentType(contentType);
			}
			if (file != null && contentLength >= 0) {
				if (logger.isDebugEnabled())
					logger.debug("DefaultServlet.serveFile:  contentLength=" + contentLength);
				if (contentLength < Integer.MAX_VALUE)
					resp.setContentLength((int) contentLength);
				else
					// Set the content-length as String to be able to use a long
					resp.setHeader("content-length", "" + contentLength);
			}

			InputStream renderResult = null;
			if (folder != null)
				if (content)
					// Serve the directory browser
					renderResult = renderHtml(req.getContextPath(), path, folder, req);

			// Copy the input stream to our output stream (if requested)
			if (content) {
				try {
					resp.setBufferSize(output);
				} catch (IllegalStateException e) {
					// Silent catch
				}
				if (ostream != null)
					copy(file, renderResult, ostream, req, null);
				else
					copy(file, renderResult, writer, req, null);
				updateAccounting(user, new Date(), contentLength);
			}
		} else {
			if (ranges == null || ranges.isEmpty())
				return;
			// Partial content response.
			resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

			if (ranges.size() == 1) {
				Range range = (Range) ranges.get(0);
				resp.addHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + range.length);
				long length = range.end - range.start + 1;
				if (length < Integer.MAX_VALUE)
					resp.setContentLength((int) length);
				else
					// Set the content-length as String to be able to use a long
					resp.setHeader("content-length", "" + length);

				if (contentType != null) {
					if (logger.isDebugEnabled())
						logger.debug("DefaultServlet.serveFile:  contentType='" + contentType + "'");
					resp.setContentType(contentType);
				}

				if (content) {
					try {
						resp.setBufferSize(output);
					} catch (IllegalStateException e) {
						// Silent catch
					}
					if (ostream != null)
						copy(file, ostream, range, req, null);
					else
						copy(file, writer, range, req, null);
					updateAccounting(user, new Date(), contentLength);
				}

			} else {

				resp.setContentType("multipart/byteranges; boundary=" + mimeSeparation);

				if (content) {
					try {
						resp.setBufferSize(output);
					} catch (IllegalStateException e) {
						// Silent catch
					}
					if (ostream != null)
						copy(file, ostream, ranges.iterator(), contentType, req, null);
					else
						copy(file, writer, ranges.iterator(), contentType, req, null);
				}

			}

		}

	}

	/**
	 * Retrieve the last modified date of a resource in HTTP format.
	 *
	 * @param auditInfo the audit info for the specified resource
	 * @return the last modified date in HTTP format
	 */
	protected String getLastModifiedHttp(AuditInfo auditInfo) {
		Date modifiedDate = auditInfo.getModificationDate();
		if (modifiedDate == null)
			modifiedDate = auditInfo.getCreationDate();
		if (modifiedDate == null)
			modifiedDate = new Date();
		String lastModifiedHttp = null;
		synchronized (format) {
			lastModifiedHttp = format.format(modifiedDate);
		}
		return lastModifiedHttp;
	}

	/**
	 * Parse the range header.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param file
	 * @param oldBody the old version of the file, if requested
	 * @return Vector of ranges
	 * @throws IOException
	 */
	protected ArrayList parseRange(HttpServletRequest request, HttpServletResponse response, FileHeader file, FileBody oldBody) throws IOException {
		// Checking If-Range
		String headerValue = request.getHeader("If-Range");
		if (headerValue != null) {
			long headerValueTime = -1L;
			try {
				headerValueTime = request.getDateHeader("If-Range");
			} catch (IllegalArgumentException e) {
				// Do nothing.
			}

			String eTag = getETag(file, oldBody);
			long lastModified = oldBody == null ?
						file.getAuditInfo().getModificationDate().getTime() :
						oldBody.getAuditInfo().getModificationDate().getTime();

			if (headerValueTime == -1L) {
				// If the ETag the client gave does not match the entity
				// etag, then the entire entity is returned.
				if (!eTag.equals(headerValue.trim()))
					return FULL;
			} else
			// If the timestamp of the entity the client got is older than
			// the last modification date of the entity, the entire entity
			// is returned.
			if (lastModified > headerValueTime + 1000)
				return FULL;
		}

		long fileLength = oldBody == null ? file.getCurrentBody().getFileSize() : oldBody.getFileSize();
		if (fileLength == 0)
			return null;

		// Retrieving the range header (if any is specified).
		String rangeHeader = request.getHeader("Range");

		if (rangeHeader == null)
			return null;
		// bytes is the only range unit supported (and I don't see the point
		// of adding new ones).
		if (!rangeHeader.startsWith("bytes")) {
			response.addHeader("Content-Range", "bytes */" + fileLength);
			response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
			return null;
		}

		rangeHeader = rangeHeader.substring(6);

		// Vector that will contain all the ranges which are successfully
		// parsed.
		ArrayList<Range> result = new ArrayList<Range>();
		StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");
		// Parsing the range list
		while (commaTokenizer.hasMoreTokens()) {
			String rangeDefinition = commaTokenizer.nextToken().trim();

			Range currentRange = new Range();
			currentRange.length = fileLength;

			int dashPos = rangeDefinition.indexOf('-');

			if (dashPos == -1) {
				response.addHeader("Content-Range", "bytes */" + fileLength);
				response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
				return null;
			}

			if (dashPos == 0)
				try {
					long offset = Long.parseLong(rangeDefinition);
					currentRange.start = fileLength + offset;
					currentRange.end = fileLength - 1;
				} catch (NumberFormatException e) {
					response.addHeader("Content-Range", "bytes */" + fileLength);
					response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
					return null;
				}
			else
				try {
					currentRange.start = Long.parseLong(rangeDefinition.substring(0, dashPos));
					if (dashPos < rangeDefinition.length() - 1)
						currentRange.end = Long.parseLong(rangeDefinition.substring(dashPos + 1, rangeDefinition.length()));
					else
						currentRange.end = fileLength - 1;
				} catch (NumberFormatException e) {
					response.addHeader("Content-Range", "bytes */" + fileLength);
					response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
					return null;
				}

			if (!currentRange.validate()) {
				response.addHeader("Content-Range", "bytes */" + fileLength);
				response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
				return null;
			}
			result.add(currentRange);
		}
		return result;
	}

	/**
	 * Check if the conditions specified in the optional If headers are
	 * satisfied.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param file the file resource against which the checks will be made
	 * @param oldBody the old version of the file, if requested
	 * @return boolean true if the resource meets all the specified conditions,
	 *         and false if any of the conditions is not satisfied, in which
	 *         case request processing is stopped
	 * @throws IOException
	 */
	protected boolean checkIfHeaders(HttpServletRequest request, HttpServletResponse response,
				FileHeader file, FileBody oldBody) throws IOException {
		// TODO : Checking the WebDAV If header
		return checkIfMatch(request, response, file, oldBody) &&
				checkIfModifiedSince(request, response, file, oldBody) &&
				checkIfNoneMatch(request, response, file, oldBody) &&
				checkIfUnmodifiedSince(request, response, file, oldBody);
	}

	/**
	 * Check if the if-match condition is satisfied.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param file the file object
	 * @param oldBody the old version of the file, if requested
	 * @return boolean true if the resource meets the specified condition, and
	 *         false if the condition is not satisfied, in which case request
	 *         processing is stopped
	 * @throws IOException
	 */
	private boolean checkIfMatch(HttpServletRequest request, HttpServletResponse response,
				FileHeader file, FileBody oldBody) throws IOException {
		String eTag = getETag(file, oldBody);
		String headerValue = request.getHeader("If-Match");
		if (headerValue != null)
			if (headerValue.indexOf('*') == -1) {
				StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
				boolean conditionSatisfied = false;
				while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
					String currentToken = commaTokenizer.nextToken();
					if (currentToken.trim().equals(eTag))
						conditionSatisfied = true;
				}
				// If none of the given ETags match, 412 Precodition failed is
				// sent back.
				if (!conditionSatisfied) {
					response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
					return false;
				}
			}
		return true;
	}

	/**
	 * Check if the if-modified-since condition is satisfied.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param file the file object
	 * @param oldBody the old version of the file, if requested
	 * @return boolean true if the resource meets the specified condition, and
	 *         false if the condition is not satisfied, in which case request
	 *         processing is stopped
	 */
	private boolean checkIfModifiedSince(HttpServletRequest request,
				HttpServletResponse response, FileHeader file, FileBody oldBody) {
		try {
			long headerValue = request.getDateHeader("If-Modified-Since");
			long lastModified = oldBody == null ?
						file.getAuditInfo().getModificationDate().getTime() :
						oldBody.getAuditInfo().getModificationDate().getTime();
			if (headerValue != -1)
				// If an If-None-Match header has been specified, if modified
				// since is ignored.
				if (request.getHeader("If-None-Match") == null && lastModified < headerValue + 1000) {
					// The entity has not been modified since the date
					// specified by the client. This is not an error case.
					response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					response.setHeader("ETag", getETag(file, oldBody));
					return false;
				}
		} catch (IllegalArgumentException illegalArgument) {
			return true;
		}
		return true;
	}

	/**
	 * Check if the if-none-match condition is satisfied.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param file the file object
	 * @param oldBody the old version of the file, if requested
	 * @return boolean true if the resource meets the specified condition, and
	 *         false if the condition is not satisfied, in which case request
	 *         processing is stopped
	 * @throws IOException
	 */
	private boolean checkIfNoneMatch(HttpServletRequest request,
				HttpServletResponse response, FileHeader file, FileBody oldBody)
			throws IOException {
		String eTag = getETag(file, oldBody);
		String headerValue = request.getHeader("If-None-Match");
		if (headerValue != null) {
			boolean conditionSatisfied = false;
			if (!headerValue.equals("*")) {
				StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
				while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
					String currentToken = commaTokenizer.nextToken();
					if (currentToken.trim().equals(eTag))
						conditionSatisfied = true;
				}
			} else
				conditionSatisfied = true;
			if (conditionSatisfied) {
				// For GET and HEAD, we should respond with 304 Not Modified.
				// For every other method, 412 Precondition Failed is sent
				// back.
				if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
					response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					response.setHeader("ETag", getETag(file, oldBody));
					return false;
				}
				response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if the if-unmodified-since condition is satisfied.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param file the file object
	 * @param oldBody the old version of the file, if requested
	 * @return boolean true if the resource meets the specified condition, and
	 *         false if the condition is not satisfied, in which case request
	 *         processing is stopped
	 * @throws IOException
	 */
	private boolean checkIfUnmodifiedSince(HttpServletRequest request,
				HttpServletResponse response, FileHeader file, FileBody oldBody)
			throws IOException {
		try {
			long lastModified = oldBody == null ?
						file.getAuditInfo().getModificationDate().getTime() :
						oldBody.getAuditInfo().getModificationDate().getTime();
			long headerValue = request.getDateHeader("If-Unmodified-Since");
			if (headerValue != -1)
				if (lastModified >= headerValue + 1000) {
					// The entity has not been modified since the date
					// specified by the client. This is not an error case.
					response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
					return false;
				}
		} catch (IllegalArgumentException illegalArgument) {
			return true;
		}
		return true;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param file the file resource
	 * @param is
	 * @param ostream The output stream to write to
	 * @param req the HTTP request
	 * @param oldBody the old version of the file, if requested
	 * @exception IOException if an input/output error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void copy(FileHeader file, InputStream is, ServletOutputStream ostream,
				HttpServletRequest req, FileBody oldBody) throws IOException,
				ObjectNotFoundException, InsufficientPermissionsException, RpcException {
		IOException exception = null;
		InputStream resourceInputStream = null;
		User user = getUser(req);
		if (user == null)
			throw new ObjectNotFoundException("No user or owner specified");
		if (file != null)
			resourceInputStream = oldBody == null ?
						getService().getFileContents(user.getId(), file.getId()) :
						getService().getFileContents(user.getId(), file.getId(), oldBody.getId());
		else
			resourceInputStream = is;

		InputStream istream = new BufferedInputStream(resourceInputStream, input);
		// Copy the input stream to the output stream
		exception = copyRange(istream, ostream);
		// Clean up the input stream
		istream.close();
		// Rethrow any exception that has occurred
		if (exception != null)
			throw exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param istream The input stream to read from
	 * @param ostream The output stream to write to
	 * @return Exception which occurred during processing
	 */
	private IOException copyRange(InputStream istream, ServletOutputStream ostream) {
		// Copy the input stream to the output stream
		IOException exception = null;
		byte buffer[] = new byte[input];
		int len = buffer.length;
		while (true)
			try {
				len = istream.read(buffer);
				if (len == -1)
					break;
				ostream.write(buffer, 0, len);
			} catch (IOException e) {
				exception = e;
				len = -1;
				break;
			}
		return exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param file
	 * @param is
	 * @param resourceInfo The resource info
	 * @param writer The writer to write to
	 * @param req the HTTP request
	 * @param oldBody the old version of the file, if requested
	 * @exception IOException if an input/output error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void copy(FileHeader file, InputStream is, PrintWriter writer,
				HttpServletRequest req, FileBody oldBody) throws IOException,
				ObjectNotFoundException, InsufficientPermissionsException, RpcException {
		IOException exception = null;
		
		User user = getUser(req);
		InputStream resourceInputStream = null;
		if (file != null)
			resourceInputStream = oldBody == null ?
						getService().getFileContents(user.getId(), file.getId()) :
						getService().getFileContents(user.getId(), file.getId(), oldBody.getId());
		else
			resourceInputStream = is;

		Reader reader;
		if (fileEncoding == null)
			reader = new InputStreamReader(resourceInputStream);
		else
			reader = new InputStreamReader(resourceInputStream, fileEncoding);

		// Copy the input stream to the output stream
		exception = copyRange(reader, writer);
		// Clean up the reader
		reader.close();
		// Rethrow any exception that has occurred
		if (exception != null)
			throw exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param reader The reader to read from
	 * @param writer The writer to write to
	 * @return Exception which occurred during processing
	 */
	private IOException copyRange(Reader reader, PrintWriter writer) {
		// Copy the input stream to the output stream
		IOException exception = null;
		char buffer[] = new char[input];
		int len = buffer.length;
		while (true)
			try {
				len = reader.read(buffer);
				if (len == -1)
					break;
				writer.write(buffer, 0, len);
			} catch (IOException e) {
				exception = e;
				len = -1;
				break;
			}
		return exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param file
	 * @param writer The writer to write to
	 * @param ranges Enumeration of the ranges the client wanted to retrieve
	 * @param contentType Content type of the resource
	 * @param req the HTTP request
	 * @param oldBody the old version of the file, if requested
	 * @exception IOException if an input/output error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void copy(FileHeader file, PrintWriter writer, Iterator ranges,
				String contentType, HttpServletRequest req, FileBody oldBody)
			throws IOException, ObjectNotFoundException, InsufficientPermissionsException, RpcException {
		User user = getUser(req);
		IOException exception = null;
		while (exception == null && ranges.hasNext()) {
			InputStream resourceInputStream = oldBody == null ?
						getService().getFileContents(user.getId(), file.getId()) :
						getService().getFileContents(user.getId(), file.getId(), oldBody.getId());
			Reader reader;
			if (fileEncoding == null)
				reader = new InputStreamReader(resourceInputStream);
			else
				reader = new InputStreamReader(resourceInputStream, fileEncoding);
			Range currentRange = (Range) ranges.next();
			// Writing MIME header.
			writer.println();
			writer.println("--" + mimeSeparation);
			if (contentType != null)
				writer.println("Content-Type: " + contentType);
			writer.println("Content-Range: bytes " + currentRange.start + "-" + currentRange.end + "/" + currentRange.length);
			writer.println();
			// Printing content
			exception = copyRange(reader, writer, currentRange.start, currentRange.end);
			reader.close();
		}
		writer.println();
		writer.print("--" + mimeSeparation + "--");
		// Rethrow any exception that has occurred
		if (exception != null)
			throw exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param istream The input stream to read from
	 * @param ostream The output stream to write to
	 * @param start Start of the range which will be copied
	 * @param end End of the range which will be copied
	 * @return Exception which occurred during processing
	 */
	private IOException copyRange(InputStream istream, ServletOutputStream ostream, long start, long end) {
		if (logger.isDebugEnabled())
			logger.debug("Serving bytes:" + start + "-" + end);
		try {
			istream.skip(start);
		} catch (IOException e) {
			return e;
		}
		IOException exception = null;
		long bytesToRead = end - start + 1;
		byte buffer[] = new byte[input];
		int len = buffer.length;
		while (bytesToRead > 0 && len >= buffer.length) {
			try {
				len = istream.read(buffer);
				if (bytesToRead >= len) {
					ostream.write(buffer, 0, len);
					bytesToRead -= len;
				} else {
					ostream.write(buffer, 0, (int) bytesToRead);
					bytesToRead = 0;
				}
			} catch (IOException e) {
				exception = e;
				len = -1;
			}
			if (len < buffer.length)
				break;
		}
		return exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param reader The reader to read from
	 * @param writer The writer to write to
	 * @param start Start of the range which will be copied
	 * @param end End of the range which will be copied
	 * @return Exception which occurred during processing
	 */
	private IOException copyRange(Reader reader, PrintWriter writer, long start, long end) {
		try {
			reader.skip(start);
		} catch (IOException e) {
			return e;
		}
		IOException exception = null;
		long bytesToRead = end - start + 1;
		char buffer[] = new char[input];
		int len = buffer.length;
		while (bytesToRead > 0 && len >= buffer.length) {
			try {
				len = reader.read(buffer);
				if (bytesToRead >= len) {
					writer.write(buffer, 0, len);
					bytesToRead -= len;
				} else {
					writer.write(buffer, 0, (int) bytesToRead);
					bytesToRead = 0;
				}
			} catch (IOException e) {
				exception = e;
				len = -1;
			}
			if (len < buffer.length)
				break;
		}
		return exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param file
	 * @param ostream The output stream to write to
	 * @param range Range the client wanted to retrieve
	 * @param req the HTTP request
	 * @param oldBody the old version of the file, if requested
	 * @exception IOException if an input/output error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void copy(FileHeader file, ServletOutputStream ostream, Range range,
				HttpServletRequest req, FileBody oldBody) throws IOException,
				ObjectNotFoundException, InsufficientPermissionsException, RpcException {
		IOException exception = null;
		User user = getUser(req);
		InputStream resourceInputStream = oldBody == null ?
					getService().getFileContents(user.getId(), file.getId()) :
					getService().getFileContents(user.getId(), file.getId(), oldBody.getId());
		InputStream istream = new BufferedInputStream(resourceInputStream, input);
		exception = copyRange(istream, ostream, range.start, range.end);
		// Clean up the input stream
		istream.close();
		// Rethrow any exception that has occurred
		if (exception != null)
			throw exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param file
	 * @param writer The writer to write to
	 * @param range Range the client wanted to retrieve
	 * @param req the HTTP request
	 * @param oldBody the old version of the file, if requested
	 * @exception IOException if an input/output error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void copy(FileHeader file, PrintWriter writer, Range range,
				HttpServletRequest req, FileBody oldBody) throws IOException,
				ObjectNotFoundException, InsufficientPermissionsException, RpcException {
		IOException exception = null;
		User user = getUser(req);
		InputStream resourceInputStream = oldBody == null ?
					getService().getFileContents(user.getId(), file.getId()) :
					getService().getFileContents(user.getId(), file.getId(), oldBody.getId());
		Reader reader;
		if (fileEncoding == null)
			reader = new InputStreamReader(resourceInputStream);
		else
			reader = new InputStreamReader(resourceInputStream, fileEncoding);

		exception = copyRange(reader, writer, range.start, range.end);
		// Clean up the input stream
		reader.close();
		// Rethrow any exception that has occurred
		if (exception != null)
			throw exception;
	}

	/**
	 * Copy the contents of the specified input stream to the specified output
	 * stream, and ensure that both streams are closed before returning (even in
	 * the face of an exception).
	 *
	 * @param file
	 * @param ostream The output stream to write to
	 * @param ranges Enumeration of the ranges the client wanted to retrieve
	 * @param contentType Content type of the resource
	 * @param req the HTTP request
	 * @param oldBody the old version of the file, if requested
	 * @exception IOException if an input/output error occurs
	 * @throws RpcException
	 * @throws InsufficientPermissionsException
	 * @throws ObjectNotFoundException
	 */
	protected void copy(FileHeader file, ServletOutputStream ostream,
				Iterator ranges, String contentType, HttpServletRequest req,
				FileBody oldBody) throws IOException, ObjectNotFoundException,
				InsufficientPermissionsException, RpcException {
		IOException exception = null;
		User user = getUser(req);
		while (exception == null && ranges.hasNext()) {
			InputStream resourceInputStream = oldBody == null ?
						getService().getFileContents(user.getId(), file.getId()) :
						getService().getFileContents(user.getId(), file.getId(), oldBody.getId());
			InputStream istream = new BufferedInputStream(resourceInputStream, input);
			Range currentRange = (Range) ranges.next();
			// Writing MIME header.
			ostream.println();
			ostream.println("--" + mimeSeparation);
			if (contentType != null)
				ostream.println("Content-Type: " + contentType);
			ostream.println("Content-Range: bytes " + currentRange.start + "-" + currentRange.end + "/" + currentRange.length);
			ostream.println();

			// Printing content
			exception = copyRange(istream, ostream, currentRange.start, currentRange.end);
			istream.close();
		}

		ostream.println();
		ostream.print("--" + mimeSeparation + "--");
		// Rethrow any exception that has occurred
		if (exception != null)
			throw exception;
	}

	/**
	 * Return an InputStream to an HTML representation of the contents of this
	 * directory.
	 *
	 * @param contextPath Context path to which our internal paths are relative
	 * @param path the requested path to the resource
	 * @param folder the specified directory
	 * @param req the HTTP request
	 * @return an input stream with the rendered contents
	 * @throws IOException
	 * @throws ServletException
	 */
	private InputStream renderHtml(String contextPath, String path, Folder folder, HttpServletRequest req) throws IOException, ServletException {
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
			sb.append(rewriteUrl(parent));
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
			sb.append(rewrittenContextPath);
			sb.append(rewriteUrl(path + resourceName));
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
		List<FileHeader> files;
		try {
			User user = getUser(req);
			files = getService().getFiles(user.getId(), folder.getId(), true);
		} catch (ObjectNotFoundException e) {
			throw new ServletException(e.getMessage());
		} catch (InsufficientPermissionsException e) {
			throw new ServletException(e.getMessage());
		} catch (RpcException e) {
			throw new ServletException(e.getMessage());
		}
		for (FileHeader fileLocal : files) {
			String resourceName = fileLocal.getName();
			if (resourceName.equalsIgnoreCase("WEB-INF") || resourceName.equalsIgnoreCase("META-INF"))
				continue;

			sb.append("<tr");
			if (shade)
				sb.append(" bgcolor=\"#eeeeee\"");
			sb.append(">\r\n");
			shade = !shade;

			sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
			sb.append("<a href=\"");
			sb.append(rewrittenContextPath);
			sb.append(rewriteUrl(path + resourceName));
			sb.append("\"><tt>");
			sb.append(RequestUtil.filter(resourceName));
			sb.append("</tt></a></td>\r\n");

			sb.append("<td align=\"right\"><tt>");
			sb.append(renderSize(fileLocal.getCurrentBody().getFileSize()));
			sb.append("</tt></td>\r\n");

			sb.append("<td align=\"right\"><tt>");
			sb.append(getLastModifiedHttp(fileLocal.getAuditInfo()));
			sb.append("</tt></td>\r\n");

			sb.append("</tr>\r\n");
		}

		// Render the page footer
		sb.append("</table>\r\n");

		sb.append("<HR size=\"1\" noshade=\"noshade\">");

		sb.append("<h3>").append(getServletContext().getServerInfo()).append("</h3>");
		sb.append("</body>\r\n");
		sb.append("</html>\r\n");

		// Return an input stream to the underlying bytes
		writer.write(sb.toString());
		writer.flush();
		return new ByteArrayInputStream(stream.toByteArray());

	}

	/**
	 * Render the specified file size (in bytes).
	 *
	 * @param size File size (in bytes)
	 * @return the size as a string
	 */
	protected String renderSize(long size) {
		long leftSide = size / 1024;
		long rightSide = size % 1024 / 103; // Makes 1 digit
		if (leftSide == 0 && rightSide == 0 && size > 0)
			rightSide = 1;
		return "" + leftSide + "." + rightSide + " kb";
	}

	/**
	 * Copy a resource.
	 *
	 * @param req Servlet request
	 * @param resp Servlet response
	 * @return boolean true if the copy is successful
	 * @throws IOException
	 */
	private boolean copyResource(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// Parsing destination header
		String destinationPath = req.getHeader("Destination");
		if (destinationPath == null) {
			resp.sendError(WebdavStatus.SC_BAD_REQUEST);
			return false;
		}

		// Remove url encoding from destination
		destinationPath = RequestUtil.URLDecode(destinationPath, "UTF8");

		int protocolIndex = destinationPath.indexOf("://");
		if (protocolIndex >= 0) {
			// if the Destination URL contains the protocol, we can safely
			// trim everything upto the first "/" character after "://"
			int firstSeparator = destinationPath.indexOf("/", protocolIndex + 4);
			if (firstSeparator < 0)
				destinationPath = "/";
			else
				destinationPath = destinationPath.substring(firstSeparator);
		} else {
			String hostName = req.getServerName();
			if (hostName != null && destinationPath.startsWith(hostName))
				destinationPath = destinationPath.substring(hostName.length());

			int portIndex = destinationPath.indexOf(":");
			if (portIndex >= 0)
				destinationPath = destinationPath.substring(portIndex);

			if (destinationPath.startsWith(":")) {
				int firstSeparator = destinationPath.indexOf("/");
				if (firstSeparator < 0)
					destinationPath = "/";
				else
					destinationPath = destinationPath.substring(firstSeparator);
			}
		}

		// Normalize destination path (remove '.' and '..')
		destinationPath = RequestUtil.normalize(destinationPath);

		String contextPath = req.getContextPath();
		if (contextPath != null && destinationPath.startsWith(contextPath))
			destinationPath = destinationPath.substring(contextPath.length());

		String pathInfo = req.getPathInfo();
		if (pathInfo != null) {
			String servletPath = req.getServletPath();
			if (servletPath != null && destinationPath.startsWith(servletPath))
				destinationPath = destinationPath.substring(servletPath.length());
		}

		if (logger.isDebugEnabled())
			logger.debug("Dest path :" + destinationPath);

		if (destinationPath.toUpperCase().startsWith("/WEB-INF") || destinationPath.toUpperCase().startsWith("/META-INF")) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
			return false;
		}

		String path = getRelativePath(req);

		if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF")) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
			return false;
		}

		if (destinationPath.equals(path)) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
			return false;
		}

		// Parsing overwrite header
		boolean overwrite = true;
		String overwriteHeader = req.getHeader("Overwrite");

		if (overwriteHeader != null)
			if (overwriteHeader.equalsIgnoreCase("T"))
				overwrite = true;
			else
				overwrite = false;

		User user = getUser(req);
		// Overwriting the destination
		boolean exists = true;
		try {
			getService().getResourceAtPath(user.getId(), destinationPath, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
			return false;
		}

		if (overwrite) {
			// Delete destination resource, if it exists
			if (exists) {
				if (!deleteResource(destinationPath, req, resp, true))
					return false;
			} else
				resp.setStatus(WebdavStatus.SC_CREATED);
		} else // If the destination exists, then it's a conflict
		if (exists) {
			resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
			return false;
		} else
			resp.setStatus(WebdavStatus.SC_CREATED);

		// Copying source to destination.
		Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
		boolean result;
		try {
			result = copyResource(errorList, path, destinationPath, req);
		} catch (RpcException e) {
			resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
			return false;
		}
		if (!result || !errorList.isEmpty()) {
			sendReport(req, resp, errorList);
			return false;
		}
		return true;
	}

	/**
	 * Copy a collection.
	 *
	 * @param errorList Hashtable containing the list of errors which occurred
	 *            during the copy operation
	 * @param source Path of the resource to be copied
	 * @param theDest Destination path
	 * @param req the HTTP request
	 * @return boolean true if the copy is successful
	 * @throws RpcException
	 */
	private boolean copyResource(Hashtable<String, Integer> errorList, String source, String theDest, HttpServletRequest req) throws RpcException {

		String dest = theDest;
		// Fix the destination path when copying collections.
		if (source.endsWith("/") && !dest.endsWith("/"))
			dest += "/";

		if (logger.isDebugEnabled())
			logger.debug("Copy: " + source + " To: " + dest);

		final User user = getUser(req);
		Object object = null;
		try {
			object = getService().getResourceAtPath(user.getId(), source, true);
		} catch (ObjectNotFoundException e) {
		}

		if (object instanceof Folder) {
			final Folder folderLocal = (Folder) object;
			try {
				final String des = dest;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().copyFolder(user.getId(), folderLocal.getId(), des);
						return null;
					}
				});
			} catch (ObjectNotFoundException e) {
				errorList.put(dest, new Integer(WebdavStatus.SC_CONFLICT));
				return false;
			} catch (DuplicateNameException e) {
				errorList.put(dest, new Integer(WebdavStatus.SC_CONFLICT));
				return false;
			} catch (InsufficientPermissionsException e) {
				errorList.put(dest, new Integer(WebdavStatus.SC_FORBIDDEN));
				return false;
			} catch (Exception e) {
				errorList.put(dest, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				return false;
			}

			try {
				String newSource = source;
				if (!source.endsWith("/"))
					newSource += "/";
				String newDest = dest;
				if (!dest.endsWith("/"))
					newDest += "/";
				// Recursively copy the subfolders.
				Iterator iter = folderLocal.getSubfolders().iterator();
				while (iter.hasNext()) {
					Folder subf = (Folder) iter.next();
					String resourceName = subf.getName();
					copyResource(errorList, newSource + resourceName, newDest + resourceName, req);
				}
				// Recursively copy the files.
				List<FileHeader> files;
				files = getService().getFiles(user.getId(), folderLocal.getId(), true);
				for (FileHeader file : files) {
					String resourceName = file.getName();
					copyResource(errorList, newSource + resourceName, newDest + resourceName, req);
				}
			} catch (RpcException e) {
				errorList.put(dest, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				return false;
			} catch (ObjectNotFoundException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_NOT_FOUND));
				return false;
			} catch (InsufficientPermissionsException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_FORBIDDEN));
				return false;
			}

		} else if (object instanceof FileHeader) {
			final FileHeader fileLocal = (FileHeader) object;
			try {
				final String des = dest;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().copyFile(user.getId(), fileLocal.getId(), des);
						return null;
					}
				});
			} catch (ObjectNotFoundException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				return false;
			} catch (DuplicateNameException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_CONFLICT));
				return false;
			} catch (InsufficientPermissionsException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_FORBIDDEN));
				return false;
			} catch (QuotaExceededException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_FORBIDDEN));
				return false;
			} catch (GSSIOException e) {
				errorList.put(source, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				return false;
			} catch (Exception e) {
				errorList.put(dest, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				return false;
			}
		} else {
			errorList.put(source, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
			return false;
		}
		return true;
	}

	/**
	 * Delete a resource.
	 *
	 * @param req Servlet request
	 * @param resp Servlet response
	 * @return boolean true if the deletion is successful
	 * @throws IOException
	 */
	private boolean deleteResource(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String path = getRelativePath(req);
		return deleteResource(path, req, resp, true);
	}

	/**
	 * Delete a resource.
	 *
	 * @param path Path of the resource which is to be deleted
	 * @param req Servlet request
	 * @param resp Servlet response
	 * @param setStatus Should the response status be set on successful
	 *            completion
	 * @return boolean true if the deletion is successful
	 * @throws IOException
	 */
	private boolean deleteResource(String path, HttpServletRequest req, HttpServletResponse resp, boolean setStatus) throws IOException {
		if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF")) {
			resp.sendError(WebdavStatus.SC_FORBIDDEN);
			return false;
		}
		String ifHeader = req.getHeader("If");
		if (ifHeader == null)
			ifHeader = "";

		String lockTokenHeader = req.getHeader("Lock-Token");
		if (lockTokenHeader == null)
			lockTokenHeader = "";

		if (isLocked(path, ifHeader + lockTokenHeader)) {
			resp.sendError(WebdavStatus.SC_LOCKED);
			return false;
		}

		final User user = getUser(req);
		boolean exists = true;
		Object object = null;
		try {
			object = getService().getResourceAtPath(user.getId(), path, true);
		} catch (ObjectNotFoundException e) {
			exists = false;
		} catch (RpcException e) {
			resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
			return false;
		}

		if (!exists) {
			resp.sendError(WebdavStatus.SC_NOT_FOUND);
			return false;
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
				resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
				return false;
			} catch (ObjectNotFoundException e) {
				// Although we had already found the object, it was
				// probably deleted from another thread.
				resp.sendError(WebdavStatus.SC_NOT_FOUND);
				return false;
			} catch (RpcException e) {
				resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
				return false;
			} catch (Exception e) {
				resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
				return false;
			}
		else if (folderLocal != null) {
			Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
			deleteCollection(req, folderLocal, path, errorList);
			try {
				final Folder f = folderLocal;
				new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						getService().deleteFolder(user.getId(), f.getId());
						return null;
					}
				});
			} catch (InsufficientPermissionsException e) {
				errorList.put(path, new Integer(WebdavStatus.SC_METHOD_NOT_ALLOWED));
			} catch (ObjectNotFoundException e) {
				errorList.put(path, new Integer(WebdavStatus.SC_NOT_FOUND));
			} catch (RpcException e) {
				errorList.put(path, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
			} catch (Exception e) {
				errorList.put(path, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
			}

			if (!errorList.isEmpty()) {
				sendReport(req, resp, errorList);
				return false;
			}
		}
		if (setStatus)
			resp.setStatus(WebdavStatus.SC_NO_CONTENT);
		return true;
	}

	/**
	 * Deletes a collection.
	 *
	 * @param req the HTTP request
	 * @param folder the folder whose contents will be deleted
	 * @param path Path to the collection to be deleted
	 * @param errorList Contains the list of the errors which occurred
	 */
	private void deleteCollection(HttpServletRequest req, Folder folder, String path, Hashtable<String, Integer> errorList) {

		if (logger.isDebugEnabled())
			logger.debug("Delete:" + path);

		if (path.toUpperCase().startsWith("/WEB-INF") || path.toUpperCase().startsWith("/META-INF")) {
			errorList.put(path, new Integer(WebdavStatus.SC_FORBIDDEN));
			return;
		}

		String ifHeader = req.getHeader("If");
		if (ifHeader == null)
			ifHeader = "";

		String lockTokenHeader = req.getHeader("Lock-Token");
		if (lockTokenHeader == null)
			lockTokenHeader = "";

		Iterator iter = folder.getSubfolders().iterator();
		while (iter.hasNext()) {
			Folder subf = (Folder) iter.next();
			String childName = path;
			if (!childName.equals("/"))
				childName += "/";
			childName += subf.getName();

			if (isLocked(childName, ifHeader + lockTokenHeader))
				errorList.put(childName, new Integer(WebdavStatus.SC_LOCKED));
			else
				try {
					final User user = getUser(req);
					Object object = getService().getResourceAtPath(user.getId(), childName, true);
					Folder childFolder = null;
					FileHeader childFile = null;
					if (object instanceof Folder)
						childFolder = (Folder) object;
					else
						childFile = (FileHeader) object;
					if (childFolder != null) {
						final Folder cf = childFolder;
						deleteCollection(req, childFolder, childName, errorList);
						new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
							@Override
							public Void call() throws Exception {
								getService().deleteFolder(user.getId(), cf.getId());
								return null;
							}
						});
					} else if (childFile != null) {
						final FileHeader cf = childFile;
						new TransactionHelper<Void>().tryExecute(new Callable<Void>() {
							@Override
							public Void call() throws Exception {
								getService().deleteFile(user.getId(), cf.getId());
								return null;
							}
						});
					}
				} catch (ObjectNotFoundException e) {
					errorList.put(childName, new Integer(WebdavStatus.SC_NOT_FOUND));
				} catch (InsufficientPermissionsException e) {
					errorList.put(childName, new Integer(WebdavStatus.SC_FORBIDDEN));
				} catch (RpcException e) {
					errorList.put(childName, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				} catch (Exception e) {
					errorList.put(childName, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
				}
		}
	}

	/**
	 * Send a multistatus element containing a complete error report to the
	 * client.
	 *
	 * @param req Servlet request
	 * @param resp Servlet response
	 * @param errorList List of error to be displayed
	 * @throws IOException
	 */
	private void sendReport(HttpServletRequest req, HttpServletResponse resp, Hashtable errorList) throws IOException {

		resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

		String absoluteUri = req.getRequestURI();
		String relativePath = getRelativePath(req);

		XMLWriter generatedXML = new XMLWriter();
		generatedXML.writeXMLHeader();

		generatedXML.writeElement(null, "multistatus" + generateNamespaceDeclarations(), XMLWriter.OPENING);

		Enumeration pathList = errorList.keys();
		while (pathList.hasMoreElements()) {

			String errorPath = (String) pathList.nextElement();
			int errorCode = ((Integer) errorList.get(errorPath)).intValue();

			generatedXML.writeElement(null, "response", XMLWriter.OPENING);

			generatedXML.writeElement(null, "href", XMLWriter.OPENING);
			String toAppend = errorPath.substring(relativePath.length());
			if (!toAppend.startsWith("/"))
				toAppend = "/" + toAppend;
			generatedXML.writeText(absoluteUri + toAppend);
			generatedXML.writeElement(null, "href", XMLWriter.CLOSING);
			generatedXML.writeElement(null, "status", XMLWriter.OPENING);
			generatedXML.writeText("HTTP/1.1 " + errorCode + " " + WebdavStatus.getStatusText(errorCode));
			generatedXML.writeElement(null, "status", XMLWriter.CLOSING);

			generatedXML.writeElement(null, "response", XMLWriter.CLOSING);

		}

		generatedXML.writeElement(null, "multistatus", XMLWriter.CLOSING);

		Writer writer = resp.getWriter();
		writer.write(generatedXML.toString());
		writer.close();

	}

	// --------------------------------------------- WebdavResolver Inner Class
	/**
	 * Work around for XML parsers that don't fully respect
	 * {@link DocumentBuilderFactory#setExpandEntityReferences(boolean)}.
	 * External references are filtered out for security reasons. See
	 * CVE-2007-5461.
	 */
	private class WebdavResolver implements EntityResolver {

		/**
		 * A private copy of the servlet context.
		 */
		private ServletContext context;

		/**
		 * Construct the resolver by passing the servlet context.
		 *
		 * @param theContext the servlet context
		 */
		public WebdavResolver(ServletContext theContext) {
			context = theContext;
		}

		@Override
		public InputSource resolveEntity(String publicId, String systemId) {
			context.log("The request included a reference to an external entity with PublicID " + publicId + " and SystemID " + systemId + " which was ignored");
			return new InputSource(new StringReader("Ignored external entity"));
		}
	}

	/**
	 * Returns the user making the request. This is the user whose credentials
	 * were supplied in the authorization header.
	 *
	 * @param req the HTTP request
	 * @return the user making the request
	 */
	protected User getUser(HttpServletRequest req) {
		return (User) req.getAttribute(USER_ATTRIBUTE);
	}

	/**
	 * Retrieves the user who owns the requested namespace, as specified in the
	 * REST API.
	 *
	 * @param req the HTTP request
	 * @return the owner of the namespace
	 */
	protected User getOwner(HttpServletRequest req) {
		return (User) req.getAttribute(OWNER_ATTRIBUTE);
	}

	/**
	 * Check if the if-modified-since condition is satisfied.
	 *
	 * @param request The servlet request we are processing
	 * @param response The servlet response we are creating
	 * @param folder the folder object
	 * @return boolean true if the resource meets the specified condition, and
	 *         false if the condition is not satisfied, in which case request
	 *         processing is stopped
	 */
	public boolean checkIfModifiedSince(HttpServletRequest request,
				HttpServletResponse response, Folder folder) {
		try {
			long headerValue = request.getDateHeader("If-Modified-Since");
			long lastModified = folder.getAuditInfo().getModificationDate().getTime();
			if (headerValue != -1)
				// If an If-None-Match header has been specified, if modified
				// since is ignored.
				if (request.getHeader("If-None-Match") == null && lastModified < headerValue + 1000) {
					// The entity has not been modified since the date
					// specified by the client. This is not an error case.
					response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					return false;
				}
		} catch (IllegalArgumentException illegalArgument) {
			return true;
		}
		return true;
	}

}
