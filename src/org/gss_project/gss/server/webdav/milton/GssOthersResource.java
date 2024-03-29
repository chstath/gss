/*
 * Copyright 2011 Electronic Business Systems Ltd.
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
package org.gss_project.gss.server.webdav.milton;

import static org.gss_project.gss.server.configuration.GSSConfigurationFactory.getConfiguration;
import org.gss_project.gss.common.exceptions.ObjectNotFoundException;
import org.gss_project.gss.common.exceptions.RpcException;
import org.gss_project.gss.server.domain.User;
import org.gss_project.gss.server.ejb.ExternalAPI;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bradmcevoy.http.Auth;
import com.bradmcevoy.http.CollectionResource;
import com.bradmcevoy.http.DigestResource;
import com.bradmcevoy.http.GetableResource;
import com.bradmcevoy.http.HttpManager;
import com.bradmcevoy.http.PropFindableResource;
import com.bradmcevoy.http.Range;
import com.bradmcevoy.http.Request;
import com.bradmcevoy.http.Resource;
import com.bradmcevoy.http.Request.Method;
import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.http11.auth.DigestResponse;


/**
 * @author kman
 *
 */
public class GssOthersResource implements PropFindableResource,  GetableResource, DigestResource, CollectionResource{
	private static final Logger log = LoggerFactory.getLogger(GssOthersResource.class);
    String host;
    GSSResourceFactory factory;
    User currentUser;
    
	/**
	 * 
	 */
	public GssOthersResource(String host, GSSResourceFactory factory) {
		this.host=host;
		this.factory=factory;
		
		
	}
	
	@Override
	public Date getCreateDate() {
		// TODO Auto-generated method stub
		return null;
	}

	
	@Override
	public String checkRedirect(Request arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getModifiedDate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		return GSSResourceFactory.OTHERS;
	}

	@Override
	public String getUniqueId() {
		return GSSResourceFactory.OTHERS;
	}

	@Override
	public Long getContentLength() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getContentType(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long getMaxAgeSeconds(Auth arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void sendContent(OutputStream arg0, Range arg1, Map<String, String> arg2, String arg3) throws IOException, NotAuthorizedException, BadRequestException {
		// TODO Auto-generated method stub
		
	}
	@Override
	public Object authenticate(String user, String password) {
        return factory.getSecurityManager().authenticate(user, password);
    }
	@Override
    public Object authenticate( DigestResponse digestRequest ) {
        return factory.getSecurityManager().authenticate(digestRequest);
        
        
    }

    @Override
    public boolean authorise(Request request, Method method, Auth auth) {
        return factory.getSecurityManager().authorise(request, method, auth, this);
    }
    @Override
    public String getRealm() {
        return factory.getRealm(this.host);
    }

	@Override
	public boolean isDigestAllowed() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public Resource child(String arg0) {
		for(Resource r : getChildren()){
			if(r.getName().equals(arg0))
				return r;
		}
		return null;
	}

	@Override
	public List<? extends Resource> getChildren() {
		List<GssOtherUserResource> result = new ArrayList<GssOtherUserResource>();
		List<User> users;
		try {
			users = getService().getUsersSharingFoldersForUser(getCurrentUser().getId());
			for(User u : users){
				result.add(new GssOtherUserResource(host, factory, u));
			}
		} catch (ObjectNotFoundException e) {
			log.error("Unable to find user in OthersResource",e);
			
		} catch (RpcException e) {
			// TODO Auto-generated catch block
			
		}
		
		
		return result;
	}
	
	/**
	 * Retrieve the currentUser.
	 *
	 * @return the currentUser
	 */
	public User getCurrentUser() {
		if(currentUser!=null)
			return currentUser;
		String username = HttpManager.request().getHeaders().get("authorization");
    	if(username!=null){
    		username=GSSResourceFactory.getUsernameFromAuthHeader(username);
    		try {
				currentUser = getService().getUserByUserName(username);
			} catch (RpcException e) {
				// TODO Auto-generated catch block
				return null;
			}
    	}
    	return currentUser;
	}
	
	/**
	 * A helper method that retrieves a reference to the ExternalAPI bean and
	 * stores it for future use.
	 *
	 * @return an ExternalAPI instance
	 * @throws RpcException in case an error occurs
	 */
	private ExternalAPI getService() throws RpcException {
		try {
			final Context ctx = new InitialContext();
			final Object ref = ctx.lookup(getConfiguration().getString("externalApiPath"));
			return (ExternalAPI) PortableRemoteObject.narrow(ref, ExternalAPI.class);
		} catch (final NamingException e) {
			log.error("Unable to retrieve the ExternalAPI EJB", e);
			throw new RpcException("An error occurred while contacting the naming service");
		}
	}

}
