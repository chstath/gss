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
package gr.ebs.gss.server.domain.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A group of users with common attributes.
 *
 * @author droutsis
 */
public class UserClassDTO implements Serializable {

	/**
	 * The serial version UID of the class.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The persistence ID of the object.
	 */
	private Long id;

	/**
	 * A name for this class.
	 */
	private String name;

	/**
	 * The disk quota of this user class.
	 */
	private long quota;

	/**
	 * The users belonging to this class
	 *
	 */
	private List<UserDTO> users = new ArrayList<UserDTO>();

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return name;
	}

	/**
	 * Retrieve the id.
	 *
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Modify the id.
	 *
	 * @param newId the id to set
	 */
	public void setId(final Long newId) {
		id = newId;
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
	 * @param newName the name to set
	 */
	public void setName(final String newName) {
		name = newName;
	}

	/**
	 * Retrieve the quota.
	 *
	 * @return the quota
	 */
	public long getQuota() {
		return quota;
	}

	/**
	 * Modify the quota.
	 *
	 * @param newQuota the quota to set
	 */
	public void setQuota(final long newQuota) {
		quota = newQuota;
	}

	/**
	 * Retrieve the users.
	 *
	 * @return the users
	 */
	public List<UserDTO> getUsers() {
		return users;
	}

	/**
	 * Modify the users.
	 *
	 * @param newUsers the users to set
	 */
	public void setUsers(final List<UserDTO> newUsers) {
		users = newUsers;
	}
}