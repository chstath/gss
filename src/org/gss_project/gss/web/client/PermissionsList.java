/*
 * Copyright 2008, 2009 Electronic Business Systems Ltd.
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
package org.gss_project.gss.web.client;

import java.util.Iterator;
import org.gss_project.gss.web.client.FilePropertiesDialog.Images;
import org.gss_project.gss.web.client.rest.GetCommand;
import org.gss_project.gss.web.client.rest.resource.PermissionHolder;
import org.gss_project.gss.web.client.rest.resource.UserResource;
import org.gss_project.gss.web.client.rest.resource.UserSearchResource;

import java.util.HashSet;
import java.util.Set;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.VerticalPanel;


/**
 * @author kman
 *
 */
public class PermissionsList extends Composite {

	int selectedRow = -1;
	
	int permissionCount = -1;
	
	Set<PermissionHolder> permissions = null;
	
	final Images images;
	
	final VerticalPanel permPanel = new VerticalPanel();
	
	final FlexTable permTable = new FlexTable();
	
	final String owner;
	
	PermissionHolder toRemove = null;
	
	private boolean hasChanges = false;
	
	private boolean hasAddition = false;
	
	public PermissionsList(final Images theImages, Set<PermissionHolder> thePermissions, String anOwner){
		images = theImages;
		owner = anOwner;
		permissions =  new HashSet<PermissionHolder>();
		permissions.addAll(thePermissions);
		permTable.setText(0, 0, "Users/Groups");
		permTable.setText(0, 1, "Read");
		permTable.setText(0, 2, "Write");
		permTable.setText(0, 3, "Modify Access");
		permTable.setText(0, 4, "");
		permTable.getFlexCellFormatter().setStyleName(0, 0, "props-toplabels");
		permTable.getFlexCellFormatter().setStyleName(0, 1, "props-toplabels");
		permTable.getFlexCellFormatter().setStyleName(0, 2, "props-toplabels");
		permTable.getFlexCellFormatter().setStyleName(0, 3, "props-toplabels");
		permPanel.add(permTable);
		permPanel.addStyleName("gss-TabPanelBottom");
		initWidget(permPanel);
		updateTable();
	}

	public boolean hasChanges(){
		return hasChanges || hasAddition;
	}


	public void updateTable(){
        Iterator<PermissionHolder> iter = permissions.iterator();
		handleFullNames(iter);
	}

	public void updatePermissionsAccordingToInput(){
		int i=1;
		for(PermissionHolder dto : permissions){
			/*if(dto.getId() == null)
				hasChanges =true;*/
			CheckBox r = (CheckBox) permTable.getWidget(i, 1);
			CheckBox w = (CheckBox) permTable.getWidget(i, 2);
			CheckBox m = (CheckBox) permTable.getWidget(i, 3);
			
			r.getElement().setId("permissionList.read");
			w.getElement().setId("permissionList.write");
			m.getElement().setId("permissionList.modify");
			
			if(dto.isRead() != r.getValue() || dto.isWrite() != w.getValue() || dto.isModifyACL() != m.getValue())
				hasChanges = true;
			dto.setRead(r.getValue());
			dto.setWrite(w.getValue());
			dto.setModifyACL(m.getValue());
			i++;
		}		
	}

	/**
	 * Retrieve the permissions.
	 *
	 * @return the permissions
	 */
	public Set<PermissionHolder> getPermissions() {
		return permissions;
	}

	public void addPermission(PermissionHolder permission){
		permissions.add(permission);
		hasAddition = true;
	}

	/**
	 * Examines whether or not the user's full name exists in the 
	 * userFullNameMap in the GSS.java for every element of the input list.
	 * If the user's full name does not exist in the map then a request is being made
	 * for the specific username.  
	 */
	private void handleFullNames(Iterator<PermissionHolder> iter){
        if (!iter.hasNext()) {
			showPermissionTable();
			return;
		}
		
		final PermissionHolder dto = iter.next();
		if(dto.getGroup() != null || GSS.get().findUserFullName(dto.getUser()) != null)
			handleFullNames(iter);
        else{
			findFullNameAndUpdate(dto.getUser(), iter);
		}
	}
	
	/**
	 * Shows the permission table 
	 */
	private void showPermissionTable(){
		int i = 1;
		if(toRemove != null){
			permissions.remove(toRemove);
			toRemove = null;
		}
		for(final PermissionHolder dto : permissions){
			PushButton removeButton = new PushButton(AbstractImagePrototype.create(images.delete()).createImage(), new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					toRemove = dto;
					updateTable();
					hasChanges = true;
				}
			});
						
			if(dto.getUser() != null){
				if(dto.getUser() != null && dto.getUser().equals(owner)){
					permTable.setHTML(i, 0, "<span id=permissionList.Owner>" + AbstractImagePrototype.create(images.permUser()).getHTML() + "&nbsp;Owner</span>");
					removeButton.setVisible(false);
				}else{
					permTable.setHTML(i, 0, "<span id=permissionList."+GSS.get().findUserFullName(dto.getUser())+">"+ AbstractImagePrototype.create(images.permUser()).getHTML() + "&nbsp;"+ GSS.get().findUserFullName(dto.getUser()) + "</span>");
				}
			}else if(dto.getGroup() != null){
				permTable.setHTML(i, 0, "<span id=permissionList."+dto.getGroup()+">" + AbstractImagePrototype.create(images.permGroup()).getHTML() + "&nbsp;"+ dto.getGroup() + "</span>");
			}
			
			CheckBox read = new CheckBox();
			read.setValue(dto.isRead());
			read.getElement().setId("permissionList.read");
			
			CheckBox write = new CheckBox();
			write.setValue(dto.isWrite());
			write.getElement().setId("permissionList.write");
			
			CheckBox modify = new CheckBox();
			modify.setValue(dto.isModifyACL());
			modify.getElement().setId("permissionList.modify");
			
			if (dto.getUser()!=null && dto.getUser().equals(owner)) {
				read.setEnabled(false);
				write.setEnabled(false);
				modify.setEnabled(false);
			}
			
			permTable.setWidget(i, 1, read);
			permTable.setWidget(i, 2, write);
			permTable.setWidget(i, 3, modify);
			permTable.setWidget(i, 4, removeButton);
			permTable.getFlexCellFormatter().setStyleName(i, 0, "props-labels");
			permTable.getFlexCellFormatter().setHorizontalAlignment(i, 1, HasHorizontalAlignment.ALIGN_CENTER);
			permTable.getFlexCellFormatter().setHorizontalAlignment(i, 2, HasHorizontalAlignment.ALIGN_CENTER);
			permTable.getFlexCellFormatter().setHorizontalAlignment(i, 3, HasHorizontalAlignment.ALIGN_CENTER);
			i++;		
		}
		for(; i<permTable.getRowCount(); i++)
			permTable.removeRow(i);
		hasChanges = false;
	}
	
	/**
	 * Makes a request to search for full name from a given username
	 * and continues checking the next element of the Set.
	 */

	private void findFullNameAndUpdate(final String username, final Iterator<PermissionHolder> iter){
		String path = GSS.get().getApiPath() + "users/" + username;

		GetCommand<UserSearchResource> gg = new GetCommand<UserSearchResource>(UserSearchResource.class, path, false,null) {
			@Override
			public void onComplete() {
				final UserSearchResource result = getResult();
                if (result.getUsers().isEmpty())
                    iter.remove();
                else
                    for (UserResource user : result.getUsers()){
                        String username = user.getUsername();
                        String userFullName = user.getName();
                        GSS.get().putUserToMap(username, userFullName);
                    }
                handleFullNames(iter);
			}
			@Override
			public void onError(Throwable t) {				
				GSS.get().displayError("Unable to fetch user's full name from the given username " + username);
				handleFullNames(iter);
			}
		};
		DeferredCommand.addCommand(gg);
	
	}
}
