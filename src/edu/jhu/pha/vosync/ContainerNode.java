package edu.jhu.pha.vosync;

import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;

import edu.jhu.pha.vosync.TransferJob.Direction;

public class ContainerNode {
	
	private final NodePath path;
	
	public ContainerNode(NodePath path) {
		this.path = path;
	}
	
	public NodePath getPath() {
		return this.path;
	}

	/**
	 * Synchronize the folder to the remote location
	 * @throws DropboxException 
	 */
	public void sync() throws DropboxException {
		processRemoteFolder(this.path);
	}
	
	
	private void processRemoteFolder(NodePath folder) throws DropboxException {
		Entry dirEntry = VOSync.getInstance().getApi().metadata(folder.getNodeStoragePath(), 0, null, true, null);
		for(Entry ent: dirEntry.contents) {
			NodePath curPath = new NodePath(ent.path);
			if(ent.isDir){
				processRemoteFolder(curPath);
			} else {
				boolean isStoredLocally = MetaHandler.isStored(curPath);
				if(!isStoredLocally && !ent.isDeleted){
					TransferJob job = new TransferJob(Direction.pullContent, curPath);
					TaskController.addJob(job);
				} else if (isStoredLocally) { // remove local file - deleted remotely
					TransferJob job = new TransferJob(Direction.pullDelete, curPath);
					TaskController.addJob(job);
				}
			}
		}
	}
}