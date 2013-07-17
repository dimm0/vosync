package edu.jhu.pha.vosync;

import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;

import java.nio.file.attribute.*;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.session.WebAuthSession;

import edu.jhu.pha.vosync.DbPool.SqlWorker;
import edu.jhu.pha.vosync.TransferJob.Direction;

public class DirWatcher2 extends Thread {

	private static final Logger logger = Logger.getLogger(DirWatcher2.class);

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}
	
	private DropboxAPI<WebAuthSession> api;
	
	private final WatchService watcher;
	private final Map<WatchKey, Path> keys;

	private Path startDir;

	/**
	 * Creates a WatchService and registers the given directory
	 * @throws IOException 
	 */
	DirWatcher2(Path dir, DropboxAPI<WebAuthSession> api) throws IOException {
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		this.startDir = dir;
		this.api = api;
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
	
				// print out event
				logger.debug(event.kind().name()+":"+child+" "+name+" "+key);
				
				try {
					if(Files.exists(child, new LinkOption[]{}) && Files.isHidden(child)){
						logger.error("Skipping hidden file "+child.getFileName()); // skip OS generated catalog files
					} else {
						if(event.kind() == ENTRY_CREATE) {
							if (Files.isRegularFile(child, NOFOLLOW_LINKS)) { // file modified
								TransferJob job = new TransferJob(Direction.pushContent, filePath);
								TaskController.addJob(job);
							} else if (Files.isDirectory(child, NOFOLLOW_LINKS)) { // directory contents changed
								registerAll(child);
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
}