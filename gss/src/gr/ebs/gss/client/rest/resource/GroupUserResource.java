/*
 * Copyright 2009 Electronic Business Systems Ltd.
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
package gr.ebs.gss.client.rest.resource;

import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;


/**
 * @author kman
 *
 */
public class GroupUserResource extends RestResource{
	/**
	 * @param path
	 */
	public GroupUserResource(String path) {
		super(path);
	}

	String username;
	String name;
	String home;



	/**
	 * Retrieve the username.
	 *
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Modify the username.
	 *
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Retrieve the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Modify the name.
	 *
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Retrieve the home.
	 *
	 * @return the home
	 */
	public String getHome() {
		return home;
	}

	/**
	 * Modify the home.
	 *
	 * @param home the home to set
	 */
	public void setHome(String home) {
		this.home = home;
	}

	public void createFromJSON(String text) {
		JSONObject json = (JSONObject) JSONParser.parse(text);
		name = unmarshallString(json, "name");
		home = unmarshallString(json, "home");
		username = unmarshallString(json, "username");
	}

}