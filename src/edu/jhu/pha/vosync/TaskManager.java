package edu.jhu.pha.vosync;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.WatchKey;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.DefaultListModel;
import javax.swing.ListModel;

import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.exception.DropboxException;

import edu.jhu.pha.vosync.TransferJob.Direction;

public class TaskManager extends Thread {

	private static final Logger logger = Logger.getLogger(TaskManager.class);

	/**
	 * The pool of transfer threads
	 */
	private static final ExecutorService transfersExecutor = Executors.newSingleThreadExecutor();

	private static final LinkedHashMap<NodePath, TransferJob> transfersMap = new LinkedHashMap<NodePath, TransferJob>();
	
	private static TaskManager self = null;
	
	public static DefaultListModel listModel = new DefaultListModel();
	
	public static void addJob(TransferJob job) {
		synchronized(transfersMap) {
			transfersMap.put(job.getPath(), job);
			listModel.addElement(job.getPath());
		}
		synchronized(self){
			self.notify();
		}
		logger.debug("Added "+job.getDirection()+" "+job.getPath());
	}

	public static TaskManager getInstance() {
		if(null == self){
			self = new TaskManager();
			//self.start();
		}
		return self;
	}
	
	private TaskManager() {}
	
	@Override
	public void run() {
		while(transfersMap.isEmpty())
			synchronized(self) {
				try {
					self.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		
		final NodePath curJobPath;
		final TransferJob job;
		synchronized(transfersMap) {
			curJobPath = transfersMap.keySet().iterator().next();
			job = transfersMap.remove(curJobPath);
		}
		transfersExecutor.execute(new Runnable() {
			@Override
			public void run() {
				switch (job.getDirection()) {
				case pullContent:
					try {
						DirWatcher2 watcher = VOSync.getInstance().getWatcher();
						WatchKey key = curJobPath.getNodeFilesystemPath().getParent().register(watcher.getWatcher(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
						watcher.getKeys().remove(key);
						key.cancel();

						FileOutputStream outp = new FileOutputStream(curJobPath.toFile());
						DropboxFileInfo info = VOSync.getInstance().getApi().getFile(curJobPath.getNodeStoragePath(), null, outp, null);
						outp.close();
						
						key = curJobPath.getNodeFilesystemPath().getParent().register(watcher.getWatcher(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
						
						MetaHandler.setFile(curJobPath, curJobPath.toFile(), info.getMetadata().rev);
					} catch(IOException ex) {
						logger.error("Error downloading file "+curJobPath.getNodeStoragePath()+": "+ex.getMessage());
					} catch(DropboxException ex) {
						ex.printStackTrace();
						logger.error("Error downloading file "+curJobPath.getNodeStoragePath()+": "+ex.getMessage());
					}
					break;
				case pushContent:
					break;
				case pullDelete:
					MetaHandler.delete(curJobPath);
					curJobPath.toFile().delete();
					break;
				case pushDelete:
					break;
				}
			}
		});
		return;
	}

	public void printQueue() {
		for(TransferJob job: this.transfersMap.values()) {
			logger.debug("Job: "+job.getDirection()+" "+job.getPath());
		}
	}
	
}
