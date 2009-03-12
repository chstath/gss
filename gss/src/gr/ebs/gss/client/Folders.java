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

import gr.ebs.gss.client.dnd.DnDTreeItem;
import gr.ebs.gss.client.domain.FolderDTO;
import gr.ebs.gss.client.rest.resource.FolderResource;
import gr.ebs.gss.client.rest.resource.OtherUserResource;
import gr.ebs.gss.client.rest.resource.RestResource;
import gr.ebs.gss.client.tree.FolderSubtree;
import gr.ebs.gss.client.tree.MyShareSubtree;
import gr.ebs.gss.client.tree.OthersSharesSubtree;
import gr.ebs.gss.client.tree.TrashSubtree;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TreeImages;
import com.google.gwt.user.client.ui.TreeItem;

/**
 * A tree displaying the folders in the user's file space.
 */
public class Folders extends Composite {

	/**
	 * A constant that denotes the completion of an IncrementalCommand.
	 */
	public static final boolean DONE = false;

	/**
	 * Specifies the images that will be bundled for this Composite and other
	 * inherited images that will be included in the same bundle.
	 */
	public interface Images extends TreeImages, FolderContextMenu.Images {

		/**
		 * Will bundle the file 'folder_home.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/folder_home.png")
		AbstractImagePrototype home();

		/**
		 * Will bundle the file 'folder_yellow.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/folder_yellow.png")
		AbstractImagePrototype folderYellow();

		/**
		 * Will bundle the file 'folder_green.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/mimetypes/document.png")
		AbstractImagePrototype document();

		/**
		 * Will bundle the file 'internet.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/internet.png")
		AbstractImagePrototype othersShared();

		/**
		 * Will bundle the file 'edit_user.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/edit_user.png")
		AbstractImagePrototype myShared();

		/**
		 * Will bundle the file 'folder_user.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/folder_user.png")
		AbstractImagePrototype sharedFolder();

		/**
		 * Will bundle the file 'trashcan_empty.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/trashcan_empty.png")
		AbstractImagePrototype trash();

		/**
		 * Will bundle the file 'folder_yellow_menu.png' residing in the package
		 * 'gr.ebs.gss.resources'.
		 *
		 * @return the image prototype
		 */
		@Resource("gr/ebs/gss/resources/folder_yellow_menu.png")
		AbstractImagePrototype folderContextMenu();
	}

	/**
	 * The widget's image bundle.
	 */
	private final Images images;

	/**
	 * The tree widget that displays the folder namespace.
	 */
	private PopupTree tree;

	/**
	 * A cached copy of the currently selected folder widget.
	 */

	private FolderSubtree folderSubtree;

	private TrashSubtree trashSubtree;

	private MyShareSubtree myShareSubtree;

	private OthersSharesSubtree othersSharesSubtree;

	// AbsolutePanel containingPanel = new AbsolutePanel();

	/**
	 * Constructs a new folders widget with a bundle of images.
	 *
	 * @param _images a bundle that provides the images for this widget
	 */
	public Folders(final Images _images) {
		images = _images;

		tree = new PopupTree(images);
		//ScrollPanel scr = new ScrollPanel(tree);
		//scr.setSize("100%", "100%");
		tree.setAnimationEnabled(true);
		//initWidget(scr);
		initWidget(tree);
		folderSubtree = new FolderSubtree(tree, images);
		myShareSubtree = new MyShareSubtree(tree, images);
		trashSubtree = new TrashSubtree(tree, images);
		othersSharesSubtree = new OthersSharesSubtree(tree, images);
		//setHeight("100%");

	}

	public Images getImages() {
		return images;
	}

	public void select(TreeItem item) {
		tree.processItemSelected(item, true);
	}

	public void clearSelection() {
		tree.clearSelection();
	}

	/**
	 * A helper method to simplify adding tree items that have attached images.
	 * {@link #addImageItem(TreeItem, String) code}
	 *
	 * @param parent the tree item to which the new item will be added.
	 * @param title the text associated with this item.
	 * @param imageProto the image of the item
	 * @return
	 */
	private TreeItem addImageItem(final TreeItem parent, final String title, final AbstractImagePrototype imageProto) {
		final TreeItem item = new TreeItem(imageItemHTML(imageProto, title));
		parent.addItem(item);
		return item;
	}

	/**
	 * Generates HTML for a tree item with an attached icon.
	 *
	 * @param imageProto the image icon
	 * @param title the title of the item
	 * @return the resultant HTML
	 */
	private HTML imageItemHTML(final AbstractImagePrototype imageProto, final String title) {
		HTML html = new HTML("<a class='hidden-link' href='javascript:;'><span >" + imageProto.getHTML() + "&nbsp;" + title + "</span></a>");
		return html;
	}

	public void update(TreeItem item) {
		if (isFileItem(item))
			folderSubtree.updateSubfolders((DnDTreeItem) item);
		else if (isTrash(item))
			trashSubtree.update();
		else if (isMySharedItem(item))
			myShareSubtree.update((DnDTreeItem)item);
		else if (isOthersSharedItem(item))
			othersSharesSubtree.update((DnDTreeItem) item);
	}

	public void updateFolder(final DnDTreeItem folderItem){
		if(isFileItem(folderItem)){
			folderSubtree.updateFolderAndSubfolders(folderItem);
			myShareSubtree.updateFolderAndSubfolders((DnDTreeItem) getMySharesItem());
		}
		else if(isMySharedItem(folderItem)){
			myShareSubtree.updateFolderAndSubfolders(folderItem);
			DnDTreeItem fitem = (DnDTreeItem) getUserItem(getRootItem(), folderItem.getFolderResource().getPath());
			if(fitem != null)
				folderSubtree.updateFolderAndSubfolders(fitem);
		}
		else if(isTrashItem(folderItem))
			trashSubtree.update();
		else if(isOthersShared(folderItem))
			othersSharesSubtree.updateFolderAndSubfolders(folderItem);
	}



	/**
	 * Retrieve the current.
	 *
	 * @return the current
	 */
	public TreeItem getCurrent() {
		return tree.getTreeSelectedItem();
	}

	/**
	 * Modify the current.
	 *
	 * @param _current the current to set
	 */
	void setCurrent(final TreeItem _current) {
		tree.setTreeSelectedItem(_current);
	}

	/**
	 * Checks whether a TreeItem is contained in the root folder structure
	 *
	 * @param item The TreeItem to check
	 * @return
	 */
	public boolean isFileItem(TreeItem item) {
		if (getRootOfItem(item).equals(getRootItem()))
			return true;
		return false;
	}

	/**
	 * Checks whether a TreeItem is contained in the trash folder structure
	 *
	 * @param item The TreeItem to check
	 * @return
	 */
	public boolean isTrashItem(TreeItem item) {
		if (getRootOfItem(item).equals(getTrashItem()))
			return true;
		return false;
	}

	/**
	 * Checks whether a TreeItem is contained in the trash folder structure
	 *
	 * @param item The TreeItem to check
	 * @return
	 */
	public boolean isOthersSharedItem(TreeItem item) {
		if (getRootOfItem(item).equals(getSharesItem()))
			return true;
		return false;
	}

	/**
	 * Checks whether a TreeItem is contained in the trash folder structure
	 *
	 * @param item The TreeItem to check
	 * @return
	 */
	public boolean isMySharedItem(TreeItem item) {
		if (getRootOfItem(item).equals(getMySharesItem()))
			return true;
		return false;
	}

	private TreeItem getRootOfItem(TreeItem item) {
		if (item.getParentItem() == null)
			return item;
		TreeItem toCheck = item;
		while (toCheck.getParentItem() != null) {
			toCheck = toCheck.getParentItem();
			toCheck = getRootOfItem(toCheck);
		}
		return toCheck;

	}

	public TreeItem getUserOfSharedItem(TreeItem item) {
		if (item.getUserObject() instanceof OtherUserResource)
			return item;
		TreeItem test = item;
		while (test != null && test.getParentItem() != null) {
			test = test.getParentItem();
			if (test.getUserObject() instanceof OtherUserResource)
				return test;
		}
		return null;
	}

	public boolean isTrash(TreeItem item) {
		return item.equals(getTrashItem());
	}

	public boolean isMyShares(TreeItem item) {
		return item.equals(getMySharesItem());
	}

	public boolean isOthersShared(TreeItem item) {
		return item.equals(getSharesItem());
	}

	/*
	 * Returns the Tree Item corresponding to the FolderDTO object
	 * since we need to update main file structure for untrashed folders
	 */
	public TreeItem getUserItem(FolderResource folder) {

		return getUserItem(getRootItem(), folder);
	}

	public TreeItem getOtherSharedItem(FolderResource folder) {

		return getUserItem(getSharesItem(), folder);
	}

	private TreeItem getUserItem(TreeItem parent, FolderResource folder) {
		TreeItem tmp = null;
		if (parent.getUserObject() instanceof FolderResource && (parent.getUserObject().equals(folder) || ((FolderResource) parent.getUserObject())	.getPath()
																																			.equals(folder.getPath())))
			return parent;
		for (int i = 0; i < parent.getChildCount(); i++) {
			TreeItem child = parent.getChild(i);
			if (child.getUserObject() instanceof FolderDTO) {
				FolderResource dto = (FolderResource) child.getUserObject();
				if (dto.equals(folder) || dto.getPath().equals(folder.getPath()))
					return child;
			}
			tmp = getUserItem(child, folder);
			if (tmp != null)
				return tmp;

		}
		return null;
	}



	/**
	 * Retrieve the trashItem.
	 *
	 * @return the trashItem
	 */
	public TreeItem getTrashItem() {
		return trashSubtree.getRootItem();
	}

	/**
	 * Retrieve the rootItem.
	 *
	 * @return the rootItem
	 */
	public TreeItem getRootItem() {
		return folderSubtree.getRootItem();
	}

	/**
	 * Retrieve the mySharesItem.
	 *
	 * @return the mySharesItem
	 */
	public TreeItem getMySharesItem() {
		return myShareSubtree.getRootItem();
	}

	/**
	 * Retrieve the sharesItem.
	 *
	 * @return the sharesItem
	 */
	public TreeItem getSharesItem() {
		return othersSharesSubtree.getRootItem();
	}



	public void onFolderTrash(TreeItem folder) {
		if (folder.getParentItem().getUserObject() instanceof FolderResource) {
			FolderResource folderDTO = (FolderResource) folder.getParentItem().getUserObject();
			updateFileAndShareNodes(folderDTO);
		} else
			update(getMySharesItem());
			//updateFileAndShareNodes(((FolderResource) folder.getUserObject()).getParent());
		update( getTrashItem());
		clearSelection();
		GSS.get().getFileList().updateFileCache(false);
	}

	public void onFolderDelete(TreeItem folder) {
		if (folder.getParentItem().getUserObject() instanceof FolderResource) {
			FolderResource folderDTO = (FolderResource) folder.getParentItem().getUserObject();
			updateFileAndShareNodes(folderDTO);
		} else
			update( getMySharesItem());
			//updateFileAndShareNodes(((FolderResource) folder.getUserObject()).getParent());
		GSS.get().getStatusPanel().updateStats();
		clearSelection();
		GSS.get().getFileList().updateFileCache(false);

	}

	public void onFolderCopy(TreeItem folder) {
		if (!updateFileAndShareNodes((FolderResource) folder.getUserObject()))
			update(folder);
		GSS.get().getFileList().updateFileCache(false);
		GSS.get().getStatusPanel().updateStats();
	}

	public void onFolderMove(TreeItem folder, FolderResource initialParent) {
		updateFileAndShareNodes(initialParent);
		updateFileAndShareNodes((FolderResource) folder.getUserObject());
		update(folder);
		GSS.get().getFileList().updateFileCache(false);
		GSS.get().getStatusPanel().updateStats();
		clearSelection();
	}

	public void onFolderUpdate(String path) {
		List<TreeItem> items = getItemsOfTreeForPath(path);

		/*
		if(isFileItem(folder)){
			folderSubtree.updateFolderAndSubfolders((DnDTreeItem) folder);
			TreeItem sharesFolder = getUserItem(getMySharesItem(), (FolderResource)folder.getUserObject());
			if (sharesFolder != null)
				myShareSubtree.updateFolderAndSubfolders( (DnDTreeItem)sharesFolder);
		}
		if(isMySharedItem(folder)){
			myShareSubtree.updateFolderAndSubfolders((DnDTreeItem)folder);
			TreeItem sharesFolder = getUserItem(getRootItem(), (FolderResource)folder.getUserObject());
			if (sharesFolder != null)
				folderSubtree.updateFolderAndSubfolders((DnDTreeItem) sharesFolder);
		}
		if(isOthersSharedItem(folder))
			othersSharesSubtree.updateFolderAndSubfolders((DnDTreeItem)folder);
			*/

	}

	private boolean updateFileAndShareNodes(FolderResource folder) {
		boolean updated = false;
		TreeItem sharesFolder = getUserItem(getMySharesItem(), folder);
		if (sharesFolder != null) {
			update(sharesFolder);
			updated = true;
		}
		TreeItem fileFolder = getUserItem(getRootItem(), folder);
		if (fileFolder != null) {
			update(fileFolder);
			updated = true;
		}
		return updated;

	}

	public void initialize(){
		DeferredCommand.addCommand(new Command(){

				public void execute() {
					GSS.get().showLoadingIndicator();
					folderSubtree.getRootItem().removeItems();
					trashSubtree.getRootItem().removeItems();
					myShareSubtree.getRootItem().removeItems();
					othersSharesSubtree.getRootItem().removeItems();
					update(folderSubtree.getRootItem());
					update(trashSubtree.getRootItem());
					update(myShareSubtree.getRootItem());
					update(othersSharesSubtree.getRootItem());
					GSS.get().setCurrentSelection(null);
					clearSelection();
					GSS.get().getFileList().updateFileCache(false);
					GSS.get().hideLoadingIndicator();

				}

		});
	}

	/* NEW HANDLING METHODS */
	public TreeItem getUserItem(TreeItem parent, String path) {
		TreeItem tmp = null;
		if (parent.getUserObject() instanceof RestResource &&
					 ((RestResource) parent.getUserObject()).getPath().equals(path))
			return parent;
		for (int i = 0; i < parent.getChildCount(); i++) {
			TreeItem child = parent.getChild(i);
			if (child.getUserObject() instanceof RestResource) {
				RestResource dto = (RestResource) child.getUserObject();
				if (dto.getPath().equals(path))
					return child;
			}
			tmp = getUserItem(child, path);
			if (tmp != null)
				return tmp;

		}
		return null;
	}

	public List<TreeItem> getItemsOfTreeForPath(String path){
		List<TreeItem> result = new ArrayList<TreeItem>();
		TreeItem item = null;
		item = getUserItem(getRootItem(), path);
		if(item != null)
			result.add(item);
		item = getUserItem(getMySharesItem(), path);
		if(item != null)
			result.add(item);
		item = getUserItem(getTrashItem(), path);
		if(item != null)
			result.add(item);
		item = getUserItem(getSharesItem(), path);
		if(item != null)
			result.add(item);
		return result;
	}
}
