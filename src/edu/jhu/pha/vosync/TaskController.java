package edu.jhu.pha.vosync;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;

import edu.jhu.pha.vosync.TransferJob.JobStatus;

public class TaskController extends Thread {

	private static final Logger logger = Logger.getLogger(TaskController.class);

	/**
	 * The pool of transfer threads
	 */
	private static final ExecutorService transfersExecutor = Executors.newSingleThreadExecutor();

	private static TaskController self = null;
	
	public static JobsListModel listModel = new JobsListModel();
	
	public static TaskController getInstance() {
		if(null == self){
			self = new TaskController();
			//self.start();
		}
		return self;
	}
	
	private TaskController() {}
	
	public static void addJob(TransferJob job) {
		listModel.addJob(job);
	}
	
	@Override
	public void run() {
		while(true) {
			while(listModel.isEmpty())
				synchronized(listModel) {
					try {
						listModel.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			
			final TransferJob job = listModel.popJob();
			final NodePath curJobPath = job.getPath();
			listModel.refresh();
			transfersExecutor.execute(new Runnable() {
				@Override
				public void run() {
					switch (job.getDirection()) {
					case pullContent:
						try {
//							DirWatcher2 watcher = VOSync.getInstance().getWatcher();
//							WatchKey key = curJobPath.getNodeFilesystemPath().getParent().register(watcher.getWatcher(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
//							watcher.getKeys().remove(key);
//							key.cancel();
	
							FileOutputStream outp = new FileOutputStream(curJobPath.toFile());
							DropboxFileInfo info = VOSync.getInstance().getApi().getFile(curJobPath.getNodeStoragePath(), null, outp, null);
							outp.close();
//							
//							Path nodePath = curJobPath.getNodeFilesystemPath().getParent();
//							key = nodePath.register(watcher.getWatcher(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
//							watcher.getKeys().put(key, nodePath);
//							MetaHandler.setFile(curJobPath, curJobPath.toFile(), info.getMetadata().rev);
						} catch(IOException ex) {
							logger.error("Error downloading file "+curJobPath.getNodeStoragePath()+": "+ex.getMessage());
						} catch(DropboxException ex) {
							ex.printStackTrace();
							logger.error("Error downloading file "+curJobPath.getNodeStoragePath()+": "+ex.getMessage());
						}
						break;
					case pushContent:
						NodePath path = job.getPath();
						if(!path.toFile().exists())
							return;
					
						/*if(api.metadata(relPath, 1, null, false, null).rev == MetaHandler.getRev(relPath)){
							logger.debug("File "+relPath+" not changed");
							return;
						}*/
						
						VOSync.debug("Uploading "+path);
						
						String rev = MetaHandler.getRev(path);
						
						try (InputStream inp = new FileInputStream(path.toFile())) {
							Entry fileEntry = VOSync.getInstance().getApi().putFile(path.getNodeStoragePath(), inp, path.toFile().length(), rev, null);
				
							MetaHandler.setFile(path, path.toFile(), fileEntry.rev);
				
							logger.debug(path+" put to db");
							
	//						//if the file was renamed, move the file on disk and download the current from server
	//						if(!fileEntry.fileName().equals(path.toFile().getName())){
	//							logger.error(fileEntry.fileName()+" != "+path.toFile().getName());
	//							path.toFile().renameTo(path.toFile());
	//						}
						} catch (IOException | DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
						}
	
						break;
					case pullDelete:
						MetaHandler.delete(curJobPath);
						curJobPath.toFile().delete();
						break;
					case pushDelete:
						MetaHandler.delete(job.getPath());
						try {
							VOSync.getInstance().getApi().delete(job.getPath().getNodeStoragePath());
						} catch (DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
						}
						break;
					}
					
				}
			});
		}
	}

	public void printQueue() {
		for(TransferJob job: listModel.values()) {
			logger.debug("Job: "+job.getDirection()+" "+job.getPath());
		}
	}
	
}
