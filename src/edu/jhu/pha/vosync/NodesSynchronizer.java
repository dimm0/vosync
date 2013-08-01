/*******************************************************************************
 * Copyright 2013 Johns Hopkins University
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package edu.jhu.pha.vosync;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.session.WebAuthSession;

import edu.jhu.pha.vosync.TransferJob.Direction;

public class NodesSynchronizer {
	
	private static final Logger logger = Logger.getLogger(NodesSynchronizer.class);
	static DropboxAPI<WebAuthSession> api = VOSync.getInstance().getApi();
	
	public static void syncPath(NodePath path) {
		final HashMap<NodePath, Entry> all_nodes = new HashMap<NodePath, Entry>();
		try {
			logger.debug("Synching storage "+path.getNodeStoragePath());
			Entry folderEntry = api.metadata(path.getNodeOuterPath(), 0, null, true, null);

			visitEntry(folderEntry, new DropboxEntryVisitor() {
				@Override
				public void preVisitDirectory(Entry entry) throws IOException {
					NodePath path = new NodePath(entry.path);
					if(!path.toFile().exists()){
						path.toFile().mkdirs();
					} else if(!path.toFile().isDirectory()) {
						logger.error(path.getNodeStoragePath()+" is not a folder");
					}
				}

				@Override
				public void visitFile(Entry entry) {
					NodePath path = new NodePath(entry.path);
					all_nodes.put(path, entry);
				}
			});
	
			Files.walkFileTree(NodePath.startDir, new SimpleFileVisitor<Path>() {
	    		@Override
	    		public FileVisitResult visitFile(Path fullPath, BasicFileAttributes attrs) throws IOException {
					logger.debug("visitFile "+fullPath.toString());
					NodePath path = new NodePath(fullPath);
	    			if(!all_nodes.containsKey(path)){
	    				logger.debug("Not stored or updated file from drive: "+path);
	    				all_nodes.put(path, null);
	    			}
	    			return FileVisitResult.CONTINUE;
	    		}
			});

		} catch(IOException | DropboxException ex) {
			logger.error("Error syching root dir: "+ex.getMessage());
		}

		for(NodePath curPath: all_nodes.keySet()) {
			sync(curPath, all_nodes.get(curPath));
		}
	}
	
	private static void visitEntry(final Entry entry, DropboxEntryVisitor visitor) {
		if(null != entry.contents) {
			logger.debug(entry.path+"contents size: "+entry.contents.size());
			for(Entry child: entry.contents) {
				if(!child.isDir){
					visitor.visitFile(child);
				} else if(!entry.isDeleted) {
					try {
						Entry childWithContents = api.metadata(child.path, 0, MetaHandler.getHash(child.path), true, MetaHandler.getRev(new NodePath(child.path)));
						visitEntry(childWithContents, visitor);
					} catch (DropboxException e) {
						logger.error(e.getMessage());
					}
					visitor.postVisitDirectory(child);
				}
			}
		}
	}

	
	public static void sync(NodePath path, Entry entry) {
		
		if(entry == null) { // not stored remotely
			if(MetaHandler.isStored(path)) { // present in local metadata - was removed remotely
				TransferJob job = new TransferJob(Direction.pullDelete, path);
				TaskController.addJob(job);
			} else { // Only present on drive: was added while offline
				TransferJob job = new TransferJob(Direction.pushContent, path);
				TaskController.addJob(job);
			}
			return;
		}
		
		if(entry.isDeleted){
			TransferJob job = new TransferJob(Direction.pullDelete, path);
			TaskController.addJob(job);
			return;
		}
		
		if(!path.toFile().isDirectory()) { // not a folder
			
			boolean localModified = (MetaHandler.isModified(path));
			boolean remoteModified = !MetaHandler.isCurrent(path, entry.rev);
			
			if(!localModified && !remoteModified) { // files are intact
			} else if(localModified && remoteModified) { // conflict
				File renameToFile = new File(path.toFile().getPath()+"_conflicted_copy_"+path.toFile().lastModified());
				path.toFile().renameTo(renameToFile);
				TransferJob job = new TransferJob(Direction.pullContent, path);
				TaskController.addJob(job);
			} else if(localModified) {
				TransferJob job = new TransferJob(Direction.pullContent, path);
				TaskController.addJob(job);
			} else if(remoteModified) {
				TransferJob job = new TransferJob(Direction.pushContent, path);
				TaskController.addJob(job);
			}
		
		}
	}
	
}
