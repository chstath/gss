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
package gr.ebs.gss.client;

import gr.ebs.gss.client.dnd.DnDFocusPanel;
import gr.ebs.gss.client.dnd.DnDTreeItem;
import gr.ebs.gss.client.rest.GetCommand;
import gr.ebs.gss.client.rest.MultipleHeadCommand;
import gr.ebs.gss.client.rest.RestCommand;
import gr.ebs.gss.client.rest.RestException;
import gr.ebs.gss.client.rest.resource.FileResource;
import gr.ebs.gss.client.rest.resource.FolderResource;
import gr.ebs.gss.client.rest.resource.OtherUserResource;
import gr.ebs.gss.client.rest.resource.SharedResource;
import gr.ebs.gss.client.rest.resource.TrashResource;
import gr.ebs.gss.client.rest.resource.UserResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.Widget;

/**
 * A composite that displays the list of files in a particular folder.
 */
public class FileList extends Composite implements TableListener, ClickListener {

	private HTML prevButton = new HTML("<a href='javascript:;'>&lt; Previous</a>", true);

	private HTML nextButton = new HTML("<a href='javascript:;'>Next &gt;</a>", true);

	private String showingStats = "";

	private int startIndex = 0;

	/**
	 * A constant that denotes the completion of an IncrementalCommand.
	 */
	public static final boolean DONE = false;

	private boolean clickControl = false;

	private boolean clickShift = false;

	private int firstShift = -1;

	private ArrayList<Integer> selectedRows = new ArrayList<Integer>();

	/**
	 * The context menu for the selected file.
	 */
	final DnDFocusPanel contextMenu;

	/**
	 * Specifies that the images available for this composite will be the ones
	 * available in FileContextMenu.
	 */
	public interface Images extends FileContextMenu.Images, Folders.Images {

		@Resource("gr/ebs/gss/resources/blank.gif")
		AbstractImagePrototype blank();

		@Resource("gr/ebs/gss/resources/asc.png")
		AbstractImagePrototype asc();

		@Resource("gr/ebs/gss/resources/desc.png")
		AbstractImagePrototype desc();

		@Resource("gr/ebs/gss/resources/mimetypes/document_shared.png")
		AbstractImagePrototype documentShared();

		@Resource("gr/ebs/gss/resources/mimetypes/kcmfontinst.png")
		AbstractImagePrototype wordprocessor();

		@Resource("gr/ebs/gss/resources/mimetypes/log.png")
		AbstractImagePrototype spreadsheet();

		@Resource("gr/ebs/gss/resources/mimetypes/kpresenter_kpr.png")
		AbstractImagePrototype presentation();

		@Resource("gr/ebs/gss/resources/mimetypes/acroread.png")
		AbstractImagePrototype pdf();

		@Resource("gr/ebs/gss/resources/mimetypes/image.png")
		AbstractImagePrototype image();

		@Resource("gr/ebs/gss/resources/mimetypes/video2.png")
		AbstractImagePrototype video();

		@Resource("gr/ebs/gss/resources/mimetypes/knotify.png")
		AbstractImagePrototype audio();

		@Resource("gr/ebs/gss/resources/mimetypes/html.png")
		AbstractImagePrototype html();

		@Resource("gr/ebs/gss/resources/mimetypes/txt.png")
		AbstractImagePrototype txt();

		@Resource("gr/ebs/gss/resources/mimetypes/ark2.png")
		AbstractImagePrototype zip();

		@Resource("gr/ebs/gss/resources/mimetypes/kcmfontinst_shared.png")
		AbstractImagePrototype wordprocessorShared();

		@Resource("gr/ebs/gss/resources/mimetypes/log_shared.png")
		AbstractImagePrototype spreadsheetShared();

		@Resource("gr/ebs/gss/resources/mimetypes/kpresenter_kpr_shared.png")
		AbstractImagePrototype presentationShared();

		@Resource("gr/ebs/gss/resources/mimetypes/acroread_shared.png")
		AbstractImagePrototype pdfShared();

		@Resource("gr/ebs/gss/resources/mimetypes/image_shared.png")
		AbstractImagePrototype imageShared();

		@Resource("gr/ebs/gss/resources/mimetypes/video2_shared.png")
		AbstractImagePrototype videoShared();

		@Resource("gr/ebs/gss/resources/mimetypes/knotify_shared.png")
		AbstractImagePrototype audioShared();

		@Resource("gr/ebs/gss/resources/mimetypes/html_shared.png")
		AbstractImagePrototype htmlShared();

		@Resource("gr/ebs/gss/resources/mimetypes/txt_shared.png")
		AbstractImagePrototype txtShared();

		@Resource("gr/ebs/gss/resources/mimetypes/ark2_shared.png")
		AbstractImagePrototype zipShared();

	}

	/**
	 * A label with the number of files in this folder.
	 */
	private HTML countLabel = new HTML();

	/**
	 * The table widget with the file list.
	 */
	private Grid table = new Grid(GSS.VISIBLE_FILE_COUNT + 1, 8);

	/**
	 * The navigation bar for paginating the results.
	 */
	private HorizontalPanel navBar = new HorizontalPanel();

	/**
	 * The number of files in this folder.
	 */
	int folderFileCount;

	/**
	 * Total folder size
	 */
	long folderTotalSize;

	/**
	 * A cache of the files in the list.
	 */
	private List<FileResource> files;

	/**
	 * The widget's image bundle.
	 */
	private final Images images;

	private String sortingProperty = "name";

	private boolean sortingType = true;

	private HTML nameLabel;

	private HTML versionLabel;

	private HTML sizeLabel;

	private HTML dateLabel;

	private HTML ownerLabel;

	private HTML pathLabel;

	/**
	 * Construct the file list widget. This entails setting up the widget
	 * layout, fetching the number of files in the current folder from the
	 * server and filling the local file cache of displayed files with data from
	 * the server, as well.
	 *
	 * @param _images
	 */
	public FileList(Images _images) {
		images = _images;

		prevButton.addClickListener(this);
		nextButton.addClickListener(this);

		contextMenu = new DnDFocusPanel(new HTML(images.fileContextMenu().getHTML()));
		contextMenu.addClickListener(new FileContextMenu(images, false, false));
		GSS.get().getDragController().makeDraggable(contextMenu);

		// Setup the table.
		table.setCellSpacing(0);
		table.setCellPadding(2);
		table.setWidth("100%");

		// Hook up events.
		table.addTableListener(this);

		// Create the 'navigation' bar at the upper-right.
		HorizontalPanel innerNavBar = new HorizontalPanel();
		innerNavBar.setStyleName("gss-ListNavBar");
		innerNavBar.setSpacing(8);
		innerNavBar.add(prevButton);
		innerNavBar.add(countLabel);
		innerNavBar.add(nextButton);
		navBar.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		navBar.add(innerNavBar);
		navBar.setWidth("100%");

		initWidget(table);
		setStyleName("gss-List");

		initTable();
		DeferredCommand.addCommand(new IncrementalCommand() {

			public boolean execute() {
				return fetchRootFolder();
			}
		});
		sinkEvents(Event.ONCONTEXTMENU);
		sinkEvents(Event.ONMOUSEUP);
		sinkEvents(Event.ONCLICK);
		sinkEvents(Event.ONKEYDOWN);
		sinkEvents(Event.ONDBLCLICK);
		GSS.preventIESelection();
	}

	public void onClick(Widget sender) {
		if (sender == nextButton) {
			// Move forward a page.
			clearSelectedRows();
			startIndex += GSS.VISIBLE_FILE_COUNT;
			if (startIndex >= folderFileCount)
				startIndex -= GSS.VISIBLE_FILE_COUNT;
			else
				update();
		} else if (sender == prevButton) {
			clearSelectedRows();
			// Move back a page.
			startIndex -= GSS.VISIBLE_FILE_COUNT;
			if (startIndex < 0)
				startIndex = 0;
			else
				update();
		}
	}

	@Override
	public void onBrowserEvent(Event event) {
		if (files == null || files.size() == 0) {
			if (DOM.eventGetType(event) == Event.ONCONTEXTMENU && selectedRows.size() == 0) {
				FileContextMenu fm = new FileContextMenu(images, false, true);
				fm.onEmptyEvent(event);
			}
			return;
		}
		if (DOM.eventGetType(event) == Event.ONCONTEXTMENU && selectedRows.size() != 0) {
			FileContextMenu fm = new FileContextMenu(images, false, false);
			fm.onEvent(event);
		} else if (DOM.eventGetType(event) == Event.ONCONTEXTMENU && selectedRows.size() == 0) {
			FileContextMenu fm = new FileContextMenu(images, false, true);
			fm.onEmptyEvent(event);
		} else if (DOM.eventGetType(event) == Event.ONDBLCLICK)
			if (getSelectedFiles().size() == 1) {
				GSS app = GSS.get();
				FileResource file = getSelectedFiles().get(0);
				String dateString = RestCommand.getDate();
				String resource = file.getUri().substring(app.getApiPath().length() - 1, file.getUri().length());
				String sig = app.getCurrentUserResource().getUsername() + " " +
						RestCommand.calculateSig("GET", dateString, resource,
						RestCommand.base64decode(app.getToken()));
				Window.open(file.getUri() + "?Authorization=" + URL.encodeComponent(sig) + "&Date=" + URL.encodeComponent(dateString), "_blank", "");
				event.preventDefault();
				return;
			}
		if (DOM.eventGetType(event) == Event.ONCLICK) {
			if (DOM.eventGetCtrlKey(event))
				clickControl = true;
			else
				clickControl = false;
			if (DOM.eventGetShiftKey(event)) {
				clickShift = true;
				if (selectedRows.size() == 1)
					firstShift = selectedRows.get(0) - startIndex;
				event.preventDefault();
			} else {
				clickShift = false;
				firstShift = -1;
				event.preventDefault();
			}
		}
		super.onBrowserEvent(event);
	}

	/**
	 * Retrieve the root folder for the current user.
	 *
	 * @return true if the retrieval was successful
	 */
	protected boolean fetchRootFolder() {
		UserResource user = GSS.get().getCurrentUserResource();
		if (user == null)
			return !DONE;
		// Update cache and clear selection.
		updateFileCache(true);
		return DONE;
	}

	public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
		// Select the row that was clicked (-1 to account for header row).
		if (row > folderFileCount)
			return;
		if (clickShift) {
			GWT.log("Row is: " + row + " fs: " + firstShift, null);
			if (firstShift == -1)
				firstShift = row;
			else if (row > firstShift) {
				clearSelectedRows();
				for (int i = firstShift; i < row; i++) {
					selectedRows.add(startIndex + i);
					styleRow(i, true);
				}
				GSS.get().setCurrentSelection(getSelectedFiles());
				contextMenu.setFiles(getSelectedFiles());
				makeRowDraggable(row);
			} else if (row != -1 && row == firstShift) {
				selectedRows.add(row - 1);
				styleRow(row, true);
				styleRow(row - 1, true);
				GSS.get().setCurrentSelection(getSelectedFiles());
				contextMenu.setFiles(getSelectedFiles());
				makeRowDraggable(row);
			} else if (row < firstShift) {
				GWT.log("Row is:" + row + " fs:" + firstShift, null);
				clearSelectedRows();

				for (int i = firstShift; i >= row - 1; i--) {
					selectedRows.add(startIndex + i);
					styleRow(i, true);
				}
				GSS.get().setCurrentSelection(getSelectedFiles());
				makeRowDraggable(row);
				contextMenu.setFiles(getSelectedFiles());
			}
		} else if (row > 0)
			selectRow(row - 1);
	}

	/**
	 * Initializes the table so that it contains enough rows for a full page of
	 * files.
	 */
	private void initTable() {
		nameLabel = new HTML("Name");
		nameLabel.addClickListener(new ClickListener() {

			public void onClick(Widget sender) {
				sortFiles("name");
				update();
			}

		});
		versionLabel = new HTML("Version");
		versionLabel.addClickListener(new ClickListener() {

			public void onClick(Widget sender) {
				sortFiles("version");
				update();
			}

		});
		sizeLabel = new HTML("Size");
		sizeLabel.addClickListener(new ClickListener() {

			public void onClick(Widget sender) {
				sortFiles("size");
				update();
			}

		});
		dateLabel = new HTML("Last modified");
		dateLabel.addClickListener(new ClickListener() {

			public void onClick(Widget sender) {
				sortFiles("date");
				update();
			}

		});
		ownerLabel = new HTML("Owner");
		ownerLabel.addClickListener(new ClickListener() {

			public void onClick(Widget sender) {
				sortFiles("owner");
				update();
			}

		});
		pathLabel = new HTML("Path");
		pathLabel.addClickListener(new ClickListener() {

			public void onClick(Widget sender) {
				sortFiles("path");
				update();
			}

		});
		// Create the header row.
		table.setText(0, 0, "");
		table.setWidget(0, 1, nameLabel);
		table.setWidget(0, 2, ownerLabel);
		table.setWidget(0, 3, pathLabel);
		table.setWidget(0, 4, versionLabel);
		table.setWidget(0, 5, sizeLabel);
		table.setWidget(0, 6, dateLabel);
		table.setWidget(0, 7, navBar);
		table.getRowFormatter().setStyleName(0, "gss-ListHeader");

		// Initialize the rest of the rows.
		for (int i = 1; i < GSS.VISIBLE_FILE_COUNT + 1; ++i) {
			table.setText(i, 0, "");
			table.setText(i, 1, "");
			table.setText(i, 2, "");
			table.setText(i, 3, "");
			table.setText(i, 4, "");
			table.setText(i, 5, "");
			table.setText(i, 6, "");
			table.setText(i, 7, "");
			table.getCellFormatter().setWordWrap(i, 0, false);
			table.getCellFormatter().setWordWrap(i, 1, false);
			table.getCellFormatter().setWordWrap(i, 2, false);
			table.getCellFormatter().setWordWrap(i, 3, false);
			table.getCellFormatter().setWordWrap(i, 4, false);
			table.getCellFormatter().setWordWrap(i, 5, false);
			table.getCellFormatter().setWordWrap(i, 6, false);
			table.getCellFormatter().setWordWrap(i, 7, false);
			table.getCellFormatter().setHorizontalAlignment(i, 4, HasHorizontalAlignment.ALIGN_CENTER);
		}
		prevButton.setVisible(false);
		nextButton.setVisible(false);
	}

	/**
	 * Selects the given row (relative to the current page).
	 *
	 * @param row the row to be selected
	 */
	private void selectRow(final int row) {
		if (row < folderFileCount) {
			if (clickControl)
				if (selectedRows.contains(row)) {
					int i = selectedRows.indexOf(startIndex + row);
					selectedRows.remove(i);
					styleRow(row, false);
				} else {
					selectedRows.add(startIndex + row);
					styleRow(row, true);
				}
			else if (selectedRows.size() == 1 && selectedRows.contains(row)){
				clearSelectedRows();
				return;
			}
			else {
				clearSelectedRows();
				selectedRows.add(startIndex + row);
				styleRow(row, true);
			}
			if (selectedRows.size() == 1)
				GSS.get().setCurrentSelection(files.get(selectedRows.get(0)));
			else if(selectedRows.size() == 0)
				GSS.get().setCurrentSelection(null);
			else
				GSS.get().setCurrentSelection(getSelectedFiles());
			contextMenu.setFiles(getSelectedFiles());
			makeRowDraggable(row+1);
		}
	}

	public List<FileResource> getSelectedFiles() {
		List<FileResource> result = new ArrayList();
		for (int i : selectedRows)
			result.add(files.get(i));
		return result;
	}

	/**
	 * Make the specified row look like selected or not, according to the
	 * <code>selected</code> flag.
	 *
	 * @param row
	 * @param selected
	 */
	void styleRow(final int row, final boolean selected) {
		if (row != -1 && row >= 0)
			if (selected)
				table.getRowFormatter().addStyleName(row + 1, "gss-SelectedRow");
			else
				table.getRowFormatter().removeStyleName(row + 1, "gss-SelectedRow");
	}

	/**
	 * Update the display of the file list.
	 */
	void update() {
		int count = folderFileCount;
		int max = startIndex + GSS.VISIBLE_FILE_COUNT;
		if (max > count)
			max = count;
		folderTotalSize = 0;

		// Show the selected files.
		int i = 1;
		for (; i < GSS.VISIBLE_FILE_COUNT + 1; ++i) {
			// Don't read past the end.
			// if (i > folderFileCount)
			// break;
			if (startIndex + i > folderFileCount)
				break;
			// Add a new row to the table, then set each of its columns to the
			// proper values.
			FileResource file = files.get(startIndex + i - 1);
			table.setWidget(i, 0, getFileIcon(file).createImage());
			table.getRowFormatter().addStyleName(i, "gss-fileRow");

			//add view image link for image files
			if (file.getContentType().startsWith("image/"))
				table.setHTML(i, 1, file.getName() + " <a href='" +
						GSS.get().getTopPanel().getFileMenu().getDownloadURL(file) +
						"' title='" + file.getName() + "' rel='lytebox' " +
						"onclick='myLytebox.start(this, false, false)'>" +
						"(view)" + "</a>");
			else
				table.setHTML(i, 1, file.getName());
			table.setText(i, 2, file.getOwner());
			table.setText(i, 3, file.getPath());
			table.setText(i, 4, String.valueOf(file.getVersion()));
			table.setText(i, 5, String.valueOf(file.getFileSizeAsString()));
			final DateTimeFormat formatter = DateTimeFormat.getFormat("d/M/yyyy h:mm a");
			table.setText(i, 6, formatter.format(file.getModificationDate()));
			folderTotalSize += file.getContentLength();
		}

		// Clear any remaining slots.
		for (; i < GSS.VISIBLE_FILE_COUNT + 1; ++i) {
			table.setHTML(i, 0, "&nbsp;");
			table.setHTML(i, 1, "&nbsp;");
			table.setHTML(i, 2, "&nbsp;");
			table.setHTML(i, 3, "&nbsp;");
			table.setHTML(i, 4, "&nbsp;");
			table.setHTML(i, 5, "&nbsp;");
			table.setHTML(i, 6, "&nbsp;");
			table.setHTML(i, 7, "&nbsp;");
		}

		if (folderFileCount == 0) {
			showingStats = "no files";
			prevButton.setVisible(false);
			nextButton.setVisible(false);
		} else if (folderFileCount < GSS.VISIBLE_FILE_COUNT) {
			if (folderFileCount == 1)
				showingStats = "1 file";
			else
				showingStats = folderFileCount + " files";
			showingStats += " (" + FileResource.getFileSizeAsString(folderTotalSize) + ")";
			prevButton.setVisible(false);
			nextButton.setVisible(false);
		} else {
			showingStats = "" + (startIndex + 1) + " - " + max + " of " + count + " files" + " (" + FileResource.getFileSizeAsString(folderTotalSize) + ")";
			prevButton.setVisible(startIndex != 0);
			nextButton.setVisible(startIndex + GSS.VISIBLE_FILE_COUNT < count);
		}
		updateCurrentlyShowingStats();

	}

	/**
	 * Return the proper icon based on the MIME type of the file.
	 *
	 * @param file
	 * @return the icon
	 */
	private AbstractImagePrototype getFileIcon(FileResource file) {
		String mimetype = file.getContentType();
		boolean shared = file.isShared();
		if (mimetype == null)
			return shared ? images.documentShared() : images.document();
		mimetype = mimetype.toLowerCase();
		if (mimetype.startsWith("application/pdf"))
			return shared ? images.pdfShared() : images.pdf();
		else if (mimetype.endsWith("excel"))
			return shared ? images.spreadsheetShared() : images.spreadsheet();
		else if (mimetype.endsWith("msword"))
			return shared ? images.wordprocessorShared() : images.wordprocessor();
		else if (mimetype.endsWith("powerpoint"))
			return shared ? images.presentationShared() : images.presentation();
		else if (mimetype.startsWith("application/zip") ||
					mimetype.startsWith("application/gzip") ||
					mimetype.startsWith("application/x-gzip") ||
					mimetype.startsWith("application/x-tar") ||
					mimetype.startsWith("application/x-gtar"))
			return shared ? images.zipShared() : images.zip();
		else if (mimetype.startsWith("text/html"))
			return shared ? images.htmlShared() : images.html();
		else if (mimetype.startsWith("text/plain"))
			return shared ? images.txtShared() : images.txt();
		else if (mimetype.startsWith("image/"))
			return shared ? images.imageShared() : images.image();
		else if (mimetype.startsWith("video/"))
			return shared ? images.videoShared() : images.video();
		else if (mimetype.startsWith("audio/"))
			return shared ? images.audioShared() : images.audio();
		return shared ? images.documentShared() : images.document();
	}

	/**
	 * Update status panel with currently showing file stats.
	 */
	public void updateCurrentlyShowingStats() {
		GSS.get().getStatusPanel().updateCurrentlyShowing(showingStats);
	}

	/**
	 * Adjust the height of the table by adding and removing rows as necessary.
	 *
	 * @param newHeight the new height to reach
	 */
	void resizeTableHeight(final int newHeight) {
		GWT.log("Panel: " + newHeight + ", parent: " + table.getParent().getOffsetHeight(), null);
		// Fill the rest with empty slots.
		if (newHeight > table.getOffsetHeight())
			while (newHeight > table.getOffsetHeight()) {
				table.resizeRows(table.getRowCount() + 1);
				GWT.log("Table: " + table.getOffsetHeight() + ", rows: " + table.getRowCount(), null);
			}
		else
			while (newHeight < table.getOffsetHeight()) {
				table.resizeRows(table.getRowCount() - 1);
				GWT.log("Table: " + table.getOffsetHeight() + ", rows: " + table.getRowCount(), null);
			}
	}

	public void updateFileCache(boolean updateSelectedFolder, final boolean clearSelection) {
		updateFileCache(updateSelectedFolder, clearSelection, null);
	}

	public void updateFileCache(boolean updateSelectedFolder, final boolean clearSelection, final String newFilename) {
		if (!updateSelectedFolder && !GSS.get().getFolders().getTrashItem().equals(GSS.get().getFolders().getCurrent()))
			updateFileCache(clearSelection);
		else if (GSS.get().getFolders().getCurrent() != null) {
			final DnDTreeItem folderItem = (DnDTreeItem) GSS.get().getFolders().getCurrent();
			if (folderItem.getFolderResource() != null) {
				update();
				GetCommand<FolderResource> gf = new GetCommand<FolderResource>(FolderResource.class, folderItem.getFolderResource().getUri()) {

						@Override
						public void onComplete() {
							folderItem.setUserObject(getResult());
							if(GSS.get().getFolders().isFileItem(folderItem)){
								String[] filePaths = new String[folderItem.getFolderResource().getFilePaths().size()];
								int c=0;
								for(String fpath : folderItem.getFolderResource().getFilePaths()){
									filePaths[c] = fpath + "?" + Math.random();
									c++;
								}
								MultipleHeadCommand<FileResource> getFiles = new MultipleHeadCommand<FileResource>(FileResource.class, filePaths){

									@Override
									public void onComplete(){
										List<FileResource> result = getResult();
										//remove random from path
										for(FileResource r : result){
											String p = r.getUri();
											int indexOfQuestionMark = p.lastIndexOf('?');
											if(indexOfQuestionMark>0)
												r.setUri(p.substring(0, indexOfQuestionMark));
										}
										folderItem.getFolderResource().setFiles(result);
										updateFileCache(clearSelection, newFilename);
									}

									@Override
									public void onError(String p, Throwable throwable) {
										if(throwable instanceof RestException)
											GSS.get().displayError("Unable to retrieve file details:"+((RestException)throwable).getHttpStatusText());
									}

									@Override
									public void onError(Throwable t) {
										GWT.log("", t);
										GSS.get().displayError("Unable to fetch files for folder " + folderItem.getFolderResource().getName());
									}

								};
								DeferredCommand.addCommand(getFiles);
							}
							else
								updateFileCache(clearSelection, newFilename);
						}

						@Override
						public void onError(Throwable t) {
							GWT.log("", t);
							GSS.get().displayError("Unable to fetch folder " + folderItem.getFolderResource().getName());
						}
					};
					DeferredCommand.addCommand(gf);
			} else if (folderItem.getTrashResource() != null) {
				GetCommand<TrashResource> gt = new GetCommand<TrashResource>(TrashResource.class, folderItem.getTrashResource().getUri()) {

					@Override
					public void onComplete() {
						folderItem.setUserObject(getResult());
						updateFileCache(clearSelection);
					}

					@Override
					public void onError(Throwable t) {
						if (t instanceof RestException && (((RestException) t).getHttpStatusCode() == 204 || ((RestException) t).getHttpStatusCode() == 1223)) {
							folderItem.setUserObject(new TrashResource(folderItem.getTrashResource().getUri()));
							updateFileCache(clearSelection);
						} else {
							GWT.log("", t);
							GSS.get().displayError("Unable to fetch trash resource");
						}
					}
				};
				DeferredCommand.addCommand(gt);
			} else if (folderItem.getSharedResource() != null) {
				GetCommand<SharedResource> gt = new GetCommand<SharedResource>(SharedResource.class, folderItem.getSharedResource().getUri()) {

					@Override
					public void onComplete() {
						folderItem.setUserObject(getResult());
						updateFileCache(clearSelection, newFilename);
					}

					@Override
					public void onError(Throwable t) {
						GWT.log("", t);
						GSS.get().displayError("Unable to fetch My Shares resource");
					}
				};
				DeferredCommand.addCommand(gt);
			} else if (folderItem.getOtherUserResource() != null) {
				GetCommand<OtherUserResource> gt = new GetCommand<OtherUserResource>(OtherUserResource.class, folderItem.getOtherUserResource().getUri()) {

					@Override
					public void onComplete() {
						folderItem.setUserObject(getResult());
						updateFileCache(clearSelection, newFilename);
					}

					@Override
					public void onError(Throwable t) {
						GWT.log("", t);
						GSS.get().displayError("Unable to fetch My Shares resource");
					}
				};
				DeferredCommand.addCommand(gt);
			}
		} else
			updateFileCache(clearSelection);
	}

	private void updateFileCache(boolean clearSelection) {
		updateFileCache(clearSelection, null);
	}

	/**
	 * Update the file cache with data from the server.
	 *
	 * @param userId the ID of the current user
	 * @param newFilename the new name of the previously selected file,
	 * 			if a rename operation has taken place
	 */
	private void updateFileCache(boolean clearSelection, String newFilename) {
		if (clearSelection)
			clearSelectedRows();

		clearLabels();
		sortingProperty = "name";
		nameLabel.setHTML("Name&nbsp;" + images.desc().getHTML());
		sortingType = true;
		startIndex = 0;
		final TreeItem folderItem = GSS.get().getFolders().getCurrent();
		// Validation.
		if (folderItem == null || GSS.get().getFolders().isOthersShared(folderItem)) {
			setFiles(new ArrayList<FileResource>());
			update();
			return;
		}
		if (folderItem instanceof DnDTreeItem) {
			DnDTreeItem dnd = (DnDTreeItem) folderItem;
			if (dnd.getFolderResource() != null) {
				if (GSS.get().getFolders().isTrashItem(dnd))
					setFiles(new ArrayList<FileResource>());
				else
					setFiles(dnd.getFolderResource().getFiles());

			} else if (dnd.getTrashResource() != null)
				setFiles(dnd.getTrashResource().getFiles());
			else if (dnd.getSharedResource() != null)
				setFiles(dnd.getSharedResource().getFiles());
			else if (dnd.getOtherUserResource() != null)
				setFiles(dnd.getOtherUserResource().getFiles());
			else
				setFiles(dnd.getFolderResource().getFiles());

			update();
			if (!clearSelection && selectedRows.size()==1 && newFilename!=null) {
				int row = -1;
				for (int i=1; i < GSS.VISIBLE_FILE_COUNT + 1; ++i) {
					if (startIndex + i > folderFileCount)
						break;
					FileResource file = files.get(startIndex + i - 1);
					if (newFilename.equals(file.getName())) {
						row = i-1;
						break;
					}
				}
				clearSelectedRows();
				if (row!=-1)
					selectRow(row);
			}
		}
	}

	/**
	 * Fill the file cache with data.
	 */
	public void setFiles(final List<FileResource> _files) {
		if (_files.size() > 0 && !GSS.get().getFolders().isTrash(GSS.get().getFolders().getCurrent())) {
			files = new ArrayList<FileResource>();
			for (FileResource fres : _files)
				if (!fres.isDeleted())
					files.add(fres);
		} else
			files = _files;
		Collections.sort(files, new Comparator<FileResource>() {

			public int compare(FileResource arg0, FileResource arg1) {
				return arg0.getName().compareTo(arg1.getName());
			}

		});
		folderFileCount = files.size();
	}

	private void sortFiles(final String sortProperty) {
		if (sortProperty.equals(sortingProperty))
			sortingType = !sortingType;
		else {
			sortingProperty = sortProperty;
			sortingType = true;
		}
		clearLabels();
		clearSelectedRows();
		if (files == null || files.size() == 0)
			return;
		Collections.sort(files, new Comparator<FileResource>() {

			public int compare(FileResource arg0, FileResource arg1) {
				if (sortingType)
					if (sortProperty.equals("version")) {
						versionLabel.setHTML("Version&nbsp;" + images.desc().getHTML());
						return arg0.getVersion().compareTo(arg1.getVersion());
					} else if (sortProperty.equals("owner")) {
						ownerLabel.setHTML("Owner&nbsp;" + images.desc().getHTML());
						return arg0.getOwner().compareTo(arg1.getOwner());
					} else if (sortProperty.equals("date")) {
						dateLabel.setHTML("Last modified&nbsp;" + images.desc().getHTML());
						return arg0.getModificationDate().compareTo(arg1.getModificationDate());
					} else if (sortProperty.equals("size")) {
						sizeLabel.setHTML("Size&nbsp;" + images.desc().getHTML());
						return arg0.getContentLength().compareTo(arg1.getContentLength());
					} else if (sortProperty.equals("name")) {
						nameLabel.setHTML("Name&nbsp;" + images.desc().getHTML());
						return arg0.getName().compareTo(arg1.getName());
					} else if (sortProperty.equals("path")) {
						pathLabel.setHTML("Path&nbsp;" + images.desc().getHTML());
						return arg0.getUri().compareTo(arg1.getUri());
					} else {
						nameLabel.setHTML("Name&nbsp;" + images.desc().getHTML());
						return arg0.getName().compareTo(arg1.getName());
					}
				else if (sortProperty.equals("version")) {
					versionLabel.setHTML("Version&nbsp;" + images.asc().getHTML());
					return arg1.getVersion().compareTo(arg0.getVersion());
				} else if (sortProperty.equals("owner")) {
					ownerLabel.setHTML("Owner&nbsp;" + images.asc().getHTML());
					return arg1.getOwner().compareTo(arg0.getOwner());
				} else if (sortProperty.equals("date")) {
					dateLabel.setHTML("Last modified&nbsp;" + images.asc().getHTML());
					return arg1.getModificationDate().compareTo(arg0.getModificationDate());
				} else if (sortProperty.equals("size")) {
					sizeLabel.setHTML("Size&nbsp;" + images.asc().getHTML());
					return arg1.getContentLength().compareTo(arg0.getContentLength());
				} else if (sortProperty.equals("name")) {
					nameLabel.setHTML("Name&nbsp;" + images.asc().getHTML());
					return arg1.getName().compareTo(arg0.getName());
				} else if (sortProperty.equals("path")) {
					pathLabel.setHTML("Path&nbsp;" + images.asc().getHTML());
					return arg1.getUri().compareTo(arg0.getUri());
				} else {
					nameLabel.setHTML("Name&nbsp;" + images.asc().getHTML());
					return arg1.getName().compareTo(arg0.getName());
				}
			}

		});
	}

	private void clearLabels() {
		nameLabel.setText("Name");
		versionLabel.setText("Version");
		sizeLabel.setText("Size");
		dateLabel.setText("Last modified");
		ownerLabel.setText("Owner");
		pathLabel.setText("Path");
	}

	/**
	 * Retrieve the table.
	 *
	 * @return the table
	 */
	Grid getTable() {
		return table;
	}

	/**
	 * Does the list contains the requested filename
	 *
	 * @param fileName
	 * @return true/false
	 */
	public boolean contains(String fileName) {
		for (int i = 0; i < files.size(); i++)
			if (files.get(i).getName().equals(fileName))
				return true;
		return false;
	}

	public void clearSelectedRows() {
		for (int r : selectedRows) {
			int row = r - startIndex;
			styleRow(row, false);
		}
		selectedRows.clear();
		Object sel = GSS.get().getCurrentSelection();
		if (sel instanceof FileResource || sel instanceof List)
			GSS.get().setCurrentSelection(null);
	}

	/**
	 *
	 */
	public void selectAllRows() {
		clearSelectedRows();
		int count = folderFileCount;
		if (count == 0)
			return;
		int max = startIndex + GSS.VISIBLE_FILE_COUNT;
		if (max > count)
			max = count;
		int i = 1;
		for (; i < GSS.VISIBLE_FILE_COUNT + 1; ++i) {
			// Don't read past the end.
			// if (i > folderFileCount)
			// break;
			if (startIndex + i > folderFileCount)
				break;
			selectedRows.add(startIndex + i - 1);
			styleRow(i - 1, true);
		}
		GSS.get().setCurrentSelection(getSelectedFiles());
		contextMenu.setFiles(getSelectedFiles());
		makeRowDraggable(i-1);

	}

	private void makeRowDraggable(int row){
		int contextRow = getWidgetRow(contextMenu, table);
		if (contextRow != -1)
			table.setWidget(contextRow, 0, getFileIcon(files.get(contextRow - 1)).createImage());
		contextMenu.setWidget(new HTML(getFileIcon(files.get(row - 1)).getHTML()));
		table.setWidget(row, 0, contextMenu);
	}

	private int getWidgetRow(Widget widget, Grid grid) {
		for (int row = 0; row < grid.getRowCount(); row++)
			for (int col = 0; col < grid.getCellCount(row); col++) {
				Widget w = table.getWidget(row, col);
				if (w == widget)
					return row;
			}
		return -1;
	}

}
