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

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import edu.jhu.pha.vosync.TransferJob.Direction;

public class DirWatcher2 extends Thread {

	private static final Logger logger = Logger.getLogger(DirWatcher2.class);

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}
	
	private final WatchService watcher;
	private final Map<WatchKey, Path> keys;

	private Path startDir;

	private static HashSet<Path> ignoreNodes = new HashSet<Path>();
	
	private static final ScheduledExecutorService postponeWorker = 
			  Executors.newSingleThreadScheduledExecutor();
	
	/**
	 * Creates a WatchService and registers the given directory
	 * @throws IOException 
	 */
	DirWatcher2(Path dir) throws IOException {
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		this.startDir = dir;
	}

	@Override
	public void run() {
		logger.debug("Register root "+startDir.toString());
		
		registerAll(startDir);

		if(isInterrupted())
			return;
		
		TaskController.getInstance().printQueue();
	    
	    logger.debug("Start watching");
	    
		while(!isInterrupted()) {
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey "+key.toString()+" not recognized!");
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				Kind<?> kind = event.kind();
	
				// TBD - provide example of how OVERFLOW event is handled
				if (kind == OVERFLOW) {
					continue;
				}
	
				// Context for directory entry event is the file name of entry
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				
				Path child = dir.resolve(name);
				Path relativeDir = startDir.relativize(child);
				NodePath filePath = new NodePath(fixPath(relativeDir.toString()));

				if(ignoreNodes.contains(filePath.toFile().toPath())){
					logger.debug("Ignoring path "+filePath.toFile().toPath());
					continue;
				} else {
					logger.debug("Not ignoring path "+filePath.toFile().toPath());
				}
				
	
				// print out event
				logger.debug(event.kind().name()+":"+child+" "+name+" "+key);
				
				try {
					if(!Files.exists(child, new LinkOption[]{}))
						continue;
					if(Files.isHidden(child)){
						logger.error("Skipping hidden file "+child.getFileName()); // skip OS generated catalog files
					} else {
						if(event.kind() == ENTRY_CREATE) {
							if (Files.isRegularFile(child, NOFOLLOW_LINKS)) { // file modified
								TransferJob job = new TransferJob(Direction.pushContent, filePath);
								TaskController.addJob(job);
							} else if (Files.isDirectory(child, NOFOLLOW_LINKS)) { // directory contents changed
								registerAll(child);
								NodesSynchronizer.syncPath(filePath);
							}
						} else if(event.kind() == ENTRY_DELETE) {
							logger.debug("Deleting "+filePath);
							TransferJob job = new TransferJob(Direction.pushDelete, filePath);
							TaskController.addJob(job);
							logger.debug("Deleted!");
						} else if(event.kind() == ENTRY_MODIFY) {
							if (Files.isRegularFile(child, NOFOLLOW_LINKS)) { // file modified
								TransferJob job = new TransferJob(Direction.pushContent, filePath);
								TaskController.addJob(job);
							} else if (Files.isDirectory(child, NOFOLLOW_LINKS)) { // directory contents changed
								//logger.debug("Renewing dir: "+relativeDir.toString());
								// TODO update folder date
								//MetaHandler.setFile(fileRelPath, child, rev);
							}
						}

					}
				} catch(IOException ex) {
					ex.printStackTrace();
					logger.error(ex.getMessage());
				}
			}
			
			boolean valid = key.reset();
			
			if(!valid)
				keys.remove(key);
		}
	}

	private void registerAll(Path startDir) {
		try {
			Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					logger.debug("postVisitDir "+dir.toString());
					WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
					logger.debug("Adding key: "+key.toString()+" "+dir);
					keys.put(key, dir);
	    			return FileVisitResult.CONTINUE;
	    		}
			});
		} catch(IOException ex) {
			logger.error(ex.getMessage());
			return;
		}
	}

	/**
	 * Fix the path separator
	 * @param path
	 * @return
	 */
	private static String fixPath(String path) {
		return path.replaceAll("\\\\", "/");
	}
	
	public Map<WatchKey, Path> getKeys() {
		return keys;
	}

	public WatchService getWatcher() {
		return watcher;
	}
	
	public static void setIgnoreNode(final Path node, boolean ignore) {
		logger.debug("Modifying ignore: "+node+" "+ignore);
		if(ignore)
			ignoreNodes.add(node);
		else {
			postponeWorker.schedule(new Runnable() {
				@Override
				public void run() {
					ignoreNodes.remove(node);
				}
			}, 5, TimeUnit.SECONDS);
			
		}
	}
}