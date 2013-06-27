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

	public void downloadFile(String relPath) {
		VOSync.debug("Downloading file from storage: "+relPath);
		Path filePath = FileSystems.getDefault().getPath(startDir.toString(), relPath.substring(1));
		try {
			WatchKey key = filePath.getParent().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			keys.remove(key);
			key.cancel();

			FileOutputStream outp = new FileOutputStream(filePath.toFile());
			DropboxFileInfo info = api.getFile(relPath, null, outp, null);
			outp.close();
			
			key = filePath.getParent().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			keys.put(key, filePath.getParent());
			
			MetaHandler.setFile(relPath, filePath.toFile(), info.getMetadata().rev);
		} catch(IOException ex) {
			logger.error("Error downloading file "+relPath+": "+ex.getMessage());
		} catch(DropboxException ex) {
			ex.printStackTrace();
			logger.error("Error downloading file "+relPath+": "+ex.getMessage());
		}
	}

	public Path getStartDir() {
		return startDir;
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.f
	 */
	private void registerAll(final Path start) throws IOException {
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				logger.debug("postVisitDir "+dir.toString());
				WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
				logger.debug("Adding key: "+key.toString()+" "+dir);
				keys.put(key, dir);
    			return FileVisitResult.CONTINUE;
    		}
			
    		@Override
			public FileVisitResult preVisitDirectory(Path fullDir, BasicFileAttributes attrs) throws IOException {
				logger.debug("preVisitDirectory "+fullDir.toString());
				String dir = "/"+fixPath(startDir.relativize(fullDir).toString());
				if(!MetaHandler.isStored(dir) || MetaHandler.isModified(dir, fullDir.toFile())){
					try {
						Entry folderEntry = null;
						try {
							logger.debug("Creating folder: "+dir);
							folderEntry = api.createFolder(dir);
						} catch(DropboxServerException ex) {
							if(ex.error == DropboxServerException._403_FORBIDDEN) {
								folderEntry = api.metadata(dir, 0, null, false, null);
							} else {
								logger.error(ex.getMessage());
							}
						}
						
						MetaHandler.setFile(dir, fullDir.toFile(), folderEntry.rev);
					} catch(DropboxException ex) {
						ex.printStackTrace();
					}
				}
				
				return FileVisitResult.CONTINUE;
			}

    		@Override
    		public FileVisitResult visitFile(Path fullPath, BasicFileAttributes attrs) throws IOException {
				logger.debug("visitFile "+fullPath.toString());
				String file = "/"+fixPath(startDir.relativize(fullPath).toString());
    			if(!MetaHandler.isStored(file) || MetaHandler.isModified(file, fullPath.toFile())){
    				logger.debug("Not stored or updated: "+file);
    				try {
    					uploadFile(file, fullPath);
    				} catch(Exception ex) {
    					ex.printStackTrace();
    				}
    			}
    			return FileVisitResult.CONTINUE;
    		}
		});
	}
	
	@Override
	public void run() {
		logger.debug("Register root "+startDir.toString());
		
		try {
			registerAll(startDir);
		} catch(IOException ex) {
			logger.error(ex.getMessage());
			return;
		}

		if(isInterrupted())
			return;
		
		VOSync.debug("Sync local db with drive");

	    DbPool.goSql("Synching the local db with drive",
        		"select NAME from FILES",
                new SqlWorker<Boolean>() {
                    @Override
                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	ResultSet resSet = stmt.executeQuery();
                    	while(resSet.next()){
                    		try {
	                    		String fileName = resSet.getString(1);
	                			Path filePath = FileSystems.getDefault().getPath(startDir.toString(), fileName.substring(1));
	            				if(!filePath.toFile().exists()) {
	            					logger.debug("Deleting file "+fileName+" existing in DB and not present on disk");
	    							api.delete(fileName);
	    							MetaHandler.delete(fileName);
	            				}
                    		} catch(DropboxException ex) {}
                    	}
                    	resSet.close();
                    	return true;
                    }
                }
        );

		if(isInterrupted())
			return;
		
		VOSync.debug("Sync storage");

	    syncStorage();

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
				String fileRelPath = "/"+fixPath(relativeDir.toString());
	
				// print out event
				logger.debug(event.kind().name()+":"+child+" "+name+" "+key);
				
				try {
					if(Files.exists(child, new LinkOption[]{}) && Files.isHidden(child)){
						logger.error("Skipping hidden file "+child.getFileName()); // skip OS generated catalog files
					} else {
						if(event.kind() == ENTRY_CREATE) {
							if (Files.isRegularFile(child, NOFOLLOW_LINKS)) { // file modified
								uploadFile(fileRelPath, child);
							} else if (Files.isDirectory(child, NOFOLLOW_LINKS)) { // directory contents changed
								registerAll(child);
							}
						} else if(event.kind() == ENTRY_DELETE) {
							logger.debug("Deleting "+fileRelPath);
							api.delete(fileRelPath);
							MetaHandler.delete(fileRelPath);
							logger.debug("Deleted!");
						} else if(event.kind() == ENTRY_MODIFY) {
							if (Files.isRegularFile(child, NOFOLLOW_LINKS)) { // file modified
								uploadFile(fileRelPath, child);
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
				} catch(DropboxException ex) {
					ex.printStackTrace();
					logger.error(ex.getMessage());
				}
				
			}
			
			boolean valid = key.reset();
			
			if(!valid)
				keys.remove(key);
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
	
	private void syncStorage() {
		try {
			logger.debug("Synching storage.");
			final String rootDir = "/";
			Entry folderEntry = api.metadata(rootDir, 0, MetaHandler.getHash(rootDir), true, MetaHandler.getRev(rootDir));

			//TODO handle removed entries
			
			visitEntry(folderEntry, new DropboxEntryVisitor() {
				@Override
				public void preVisitDirectory(Entry entry) throws IOException {
					if(!MetaHandler.isStored(entry.path)) {
						logger.debug("Creating local dir: "+entry.path);
						Path filePath = FileSystems.getDefault().getPath(startDir.toString(), entry.path.substring(1));
						WatchKey key = filePath.getParent().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
						keys.remove(key);
						key.cancel();

						filePath.toFile().mkdir();
						
						key = filePath.getParent().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
						keys.put(key, filePath.getParent());
						
						MetaHandler.setFile(entry.path, filePath.toFile(), entry.rev);
					}
				}

				@Override
				public void visitFile(Entry entry) {
					if(!MetaHandler.isStored(entry.path))
						downloadFile(entry.path);
					else if(!MetaHandler.isCurrent(entry.path, entry.rev)) {
						Path fullPath = FileSystems.getDefault().getPath(startDir.toString(), entry.path.substring(1));
						try {
							logger.debug("Uploading file "+entry.path);
							uploadFile(entry.path, fullPath);
						} catch (Exception e) {
							logger.error("Error uploading file: "+e.getMessage());
						}
					}
				}
			});
			
			
			
		} catch(DropboxException ex) {
			logger.error("Error syching root dir: "+ex.getMessage());
		}
	}
	
	private void uploadFile(String relPath, Path fullPath) throws DropboxException, IOException {
		if(!fullPath.toFile().exists())
			return;
		
		/*if(api.metadata(relPath, 1, null, false, null).rev == MetaHandler.getRev(relPath)){
			logger.debug("File "+relPath+" not changed");
			return;
		}*/
		
		VOSync.debug("Uploading "+relPath);
		
		String rev = MetaHandler.getRev(relPath);
		
		InputStream inp = new FileInputStream(fullPath.toFile());
		Entry fileEntry = api.putFile(relPath, inp, fullPath.toFile().length(), rev, null);
		inp.close();

		Path destFilePath = FileSystems.getDefault().getPath(fullPath.toFile().getParentFile().getPath(), fileEntry.fileName());

		MetaHandler.setFile(relPath, destFilePath.toFile(), fileEntry.rev);

		logger.debug(relPath+" put to db");
		
		//if the file was renamed, move the file on disk and download the current from server
		if(!fileEntry.fileName().equals(fullPath.toFile().getName())){
			logger.error(fileEntry.fileName()+" != "+fullPath.toFile().getName());
			fullPath.toFile().renameTo(destFilePath.toFile());
		}

	}
	
	private void visitEntry(final Entry entry, DropboxEntryVisitor visitor) {
		if(null != entry.contents) {
			logger.debug(entry.path+"contents size: "+entry.contents.size());
			for(Entry child: entry.contents) {
				if(!child.isDeleted){
					if(!child.isDir){
						visitor.visitFile(child);
					} else {
						try {
							visitor.preVisitDirectory(child);
						} catch(IOException e) {
							logger.error(e.getMessage());
						}

						try {
							Entry childWithContents = api.metadata(child.path, 0, MetaHandler.getHash(child.path), true, MetaHandler.getRev(child.path));
							visitEntry(childWithContents, visitor);
						} catch (DropboxException e) {
							logger.error(e.getMessage());
						}
						visitor.postVisitDirectory(child);
					}
				}
			}
		}
	}

}