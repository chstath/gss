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
package gr.ebs.gss.client;

import gr.ebs.gss.client.rest.GetCommand;
import gr.ebs.gss.client.rest.RestException;
import gr.ebs.gss.client.rest.resource.UserResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.KeyboardListener;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;


/**
 * A dialog box that displays the user credentials for use in other client
 * applications, such as WebDAV clients.
 *
 * @author kman
 */
public class CredentialsDialog extends DialogBox {

	private final String WIDTH_FIELD = "35em";
	private final String WIDTH_TEXT = "42em";

	private TextBox passwordBox;

	/**
	 * The 'confirm reset password' dialog box.
	 */
	public class ConfirmResetPasswordDialog extends DialogBox {

		/**
		 * The widget's constructor.
		 *
		 * @param images the supplied images
		 */
		private ConfirmResetPasswordDialog(MessagePanel.Images images) {
			// Set the dialog's caption.
			setText("Confirmation");
			setAnimationEnabled(true);
			// Create a VerticalPanel to contain the label and the buttons.
			VerticalPanel outer = new VerticalPanel();
			HorizontalPanel buttons = new HorizontalPanel();

			HTML text;
			text = new HTML("<table><tr><td>" + images.warn().getHTML() + "</td><td>" + "Are you sure you want to create a new WebDAV password?</td></tr></table>");
			text.setStyleName("gss-warnMessage");
			outer.add(text);

			// Create the 'Yes' button, along with a listener that hides the dialog
			// when the button is clicked and resets the password.
			Button ok = new Button("Yes", new ClickListener() {
				public void onClick(Widget sender) {
					resetPassword(GSS.get().getCurrentUserResource().getUri());
					hide();
				}
			});
			buttons.add(ok);
			buttons.setCellHorizontalAlignment(ok, HasHorizontalAlignment.ALIGN_CENTER);
			// Create the 'No' button, along with a listener that hides the
			// dialog when the button is clicked.
			Button cancel = new Button("No", new ClickListener() {
				public void onClick(Widget sender) {
					hide();
				}
			});
			buttons.add(cancel);
			buttons.setCellHorizontalAlignment(cancel, HasHorizontalAlignment.ALIGN_CENTER);
			buttons.setSpacing(8);
			buttons.setStyleName("gss-warnMessage");
			outer.setStyleName("gss-warnMessage");
			outer.add(buttons);
			outer.setCellHorizontalAlignment(text, HasHorizontalAlignment.ALIGN_CENTER);
			outer.setCellHorizontalAlignment(buttons, HasHorizontalAlignment.ALIGN_CENTER);
			setWidget(outer);
		}


		@Override
		public boolean onKeyDownPreview(final char key, final int modifiers) {
			// Use the popup's key preview hooks to close the dialog when
			// escape is pressed.
			switch (key) {
				case KeyboardListener.KEY_ESCAPE:
					hide();
					break;
			}

			return true;
		}

	}

	/**
	 * The widget constructor.
	 */
	public CredentialsDialog(final MessagePanel.Images images) {
		// Set the dialog's caption.
		setText("User Credentials");
		setAnimationEnabled(true);
		// Create a VerticalPanel to contain the 'about' label and the 'OK'
		// button.
		VerticalPanel outer = new VerticalPanel();
		Configuration conf = (Configuration) GWT.create(Configuration.class);
		String service = conf.serviceName();
		String webdavUrl = conf.serviceHome() + conf.webdavUrl();
		String tokenNote = conf.tokenTTLNote();
		// Create the text and set a style name so we can style it with CSS.
		HTML text = new HTML("<p>These are the user credentials that are required " +
				"for interacting with " + service + ". " +
				"You can copy and paste the username and password in the WebDAV client" +
				" in order to use " + service + " through the WebDAV interface, at:<br/> " +
				webdavUrl +
				"<br/>" + tokenNote + "</p>");
		text.setStyleName("gss-AboutText");
		text.setWidth(WIDTH_TEXT);
		outer.add(text);
		FlexTable table = new FlexTable();
		table.setText(0, 0, "Username");
		table.setText(1, 0, "Password");
		table.setText(2, 0, "Token");
		TextBox username = new TextBox();
		final GSS app = GSS.get();
		username.setText(app.getCurrentUserResource().getUsername());
		username.setReadOnly(true);
		username.setWidth(WIDTH_FIELD);
		username.addClickListener(new ClickListener () {

			public void onClick(Widget sender) {
				GSS.enableIESelection();
				((TextBox) sender).selectAll();
				GSS.preventIESelection();
			}

		});
		table.setWidget(0, 1, username);
		passwordBox = new TextBox();
		passwordBox.setText(app.getWebDAVPassword());
		passwordBox.setReadOnly(true);
		passwordBox.setWidth(WIDTH_FIELD);
		passwordBox.addClickListener(new ClickListener () {

			public void onClick(Widget sender) {
				GSS.enableIESelection();
				((TextBox) sender).selectAll();
				GSS.preventIESelection();
			}

		});
		table.setWidget(1, 1, passwordBox);

		TextBox tokenBox = new TextBox();
		tokenBox.setText(app.getToken());
		tokenBox.setReadOnly(true);
		tokenBox.setWidth(WIDTH_FIELD);
		tokenBox.addClickListener(new ClickListener () {

			public void onClick(Widget sender) {
				GSS.enableIESelection();
				((TextBox) sender).selectAll();
				GSS.preventIESelection();
			}

		});
		table.setWidget(2, 1, tokenBox);

		table.getFlexCellFormatter().setStyleName(0, 0, "props-labels");
		table.getFlexCellFormatter().setStyleName(0, 1, "props-values");
		table.getFlexCellFormatter().setStyleName(1, 0, "props-labels");
		table.getFlexCellFormatter().setStyleName(1, 1, "props-values");
		table.getFlexCellFormatter().setStyleName(2, 0, "props-labels");
		table.getFlexCellFormatter().setStyleName(2, 1, "props-values");
		outer.add(table);

		// Create the 'OK' button, along with a listener that hides the dialog
		// when the button is clicked.
		Button confirm = new Button("Close", new ClickListener() {

			public void onClick(Widget sender) {
				hide();
			}
		});
		outer.add(confirm);
		outer.setCellHorizontalAlignment(confirm, HasHorizontalAlignment.ALIGN_CENTER);

		// Create the 'Reset password' button, along with a listener that hides the dialog
		// when the button is clicked.
		Button resetPassword = new Button("Reset Password", new ClickListener() {

			public void onClick(Widget sender) {
				ConfirmResetPasswordDialog dlg = new ConfirmResetPasswordDialog(images);
				dlg.center();
			}
		});
		outer.add(resetPassword);
		outer.setCellHorizontalAlignment(resetPassword, HasHorizontalAlignment.ALIGN_CENTER);

		outer.setSpacing(8);
		setWidget(outer);
	}

	@Override
	public boolean onKeyDownPreview(char key, int modifiers) {
		// Use the popup's key preview hooks to close the dialog when either
		// enter or escape is pressed.
		switch (key) {
			case KeyboardListener.KEY_ENTER:
			case KeyboardListener.KEY_ESCAPE:
				hide();
				break;
		}
		return true;
	}


	/**
	 * Generate an RPC request to reset WebDAV password.
	 *
	 * @param userId the Uri of the user whose password will be reset
	 */
	private void resetPassword(String userUri) {

		if (userUri == null || userUri.length() == 0) {
			GSS.get().displayError("Empty user Uri!");
			return;
		}
		GWT.log("resetPassword(" + userUri + ")", null);
		GetCommand cg = new GetCommand(UserResource.class, userUri + "?resetWebDAV"){

			@Override
			public void onComplete() {
				GSS.get().refreshWebDAVPassword();
				passwordBox.setText(GSS.get().getWebDAVPassword());
			}

			@Override
			public void onError(Throwable t) {
				GWT.log("", t);
				if(t instanceof RestException){
					int statusCode = ((RestException)t).getHttpStatusCode();
					if(statusCode == 405)
						GSS.get().displayError("You don't have the necessary permissions");
					else if(statusCode == 404)
						GSS.get().displayError("Resource does not exist");
					else
						GSS.get().displayError("Unable to reset password:"+((RestException)t).getHttpStatusText());
				}
				else
					GSS.get().displayError("System error resetting password:"+t.getMessage());
			}
		};
		DeferredCommand.addCommand(cg);

	}

}
