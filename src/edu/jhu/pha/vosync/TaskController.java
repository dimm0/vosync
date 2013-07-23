package edu.jhu.pha.vosync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.attribute.FileAttribute;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
	
	private static TransferJob curJob = null;
	
	public static TaskController getInstance() {
		if(null == self){
			self = new TaskController();
			//self.start();
		}
		return self;
	}
	
	private TaskController() {}
	
	public static void addJob(TransferJob job) {
		VOSync.debug("Added job: "+job.getDirection()+" "+job.getPath().getNodeStoragePath());
		listModel.addJob(job);
	}
	
	@Override
	public void run() {
		while(true) {
			while(listModel.isEmpty() && null == curJob)
				synchronized(listModel) {
					try {
						listModel.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			
			if(null == curJob) {
				synchronized(listModel) {
					curJob = listModel.popJob();
					listModel.refresh();
				}
			} else {
				VOSync.error("Job "+curJob.getPath().toString()+" "+curJob.getDirection()+" was not finished; retrying in 5 sec");
				try {sleep(5000);} catch(InterruptedException ex) {}
			}
			final NodePath curJobPath = curJob.getPath();
			Future<Boolean> fut = transfersExecutor.submit(new Callable<Boolean>() {
				@Override
				public Boolean call() {
					switch (curJob.getDirection()) {
					case pullContent:
						try {
							File tempFile = File.createTempFile("."+curJobPath.getNodeName(), null, curJobPath.toFile().getParentFile());
							
							FileOutputStream outp = new FileOutputStream(tempFile);
							DropboxFileInfo info = VOSync.getInstance().getApi().getFile(curJobPath.getNodeStoragePath(), null, outp, null);
							outp.close();
							
							DirWatcher2.setIgnoreNode(curJobPath.toFile().getAbsoluteFile().toPath(), true);

							tempFile.renameTo(curJobPath.toFile());
							MetaHandler.setFile(curJobPath, curJobPath.toFile(), info.getMetadata().rev);

							DirWatcher2.setIgnoreNode(curJobPath.toFile().getAbsoluteFile().toPath(), true);
						} catch(IOException | DropboxException ex) {
							ex.printStackTrace();
							logger.error("Error downloading file "+curJobPath.getNodeStoragePath()+": "+ex.getMessage());
							return false;
						}
						break;
					case pushContent:
						if(!curJobPath.toFile().exists())
							return true;
					
						/*if(api.metadata(relPath, 1, null, false, null).rev == MetaHandler.getRev(relPath)){
							logger.debug("File "+relPath+" not changed");
							return;
						}*/
						
						VOSync.debug("Uploading "+curJobPath);
						
						String rev = MetaHandler.getRev(curJobPath); // 0 if creating new file
						
						try (InputStream inp = new FileInputStream(curJobPath.toFile())) {
							if(!MetaHandler.isStored(curJobPath)) { // to prevent downloading new created file
								MetaHandler.setFile(curJobPath, curJobPath.toFile(), "0");
								logger.debug("!!!!!!!!! "+MetaHandler.isStored(curJobPath)+" "+curJobPath.getNodeStoragePath());
							}
								
							Entry fileEntry = VOSync.getInstance().getApi().putFile(curJobPath.getNodeStoragePath(), inp, curJobPath.toFile().length(), rev, null);
				
							MetaHandler.setFile(curJobPath, curJobPath.toFile(), fileEntry.rev);
				
							logger.debug(curJobPath+" put to db");
							
	//						//if the file was renamed, move the file on disk and download the current from server
	//						if(!fileEntry.fileName().equals(path.toFile().getName())){
	//							logger.error(fileEntry.fileName()+" != "+path.toFile().getName());
	//							path.toFile().renameTo(path.toFile());
	//						}
						} catch (IOException | DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return false;
						}
	
						break;
					case pullDelete:
						MetaHandler.delete(curJobPath);
						curJobPath.toFile().delete();
						break;
					case pushDelete:
						MetaHandler.delete(curJobPath);
						try {
							VOSync.getInstance().getApi().delete(curJobPath.getNodeStoragePath());
						} catch (DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return false;
						}
						break;
					}
					return true;
				}
			});
			try {
				boolean success = fut.get();
				if(success)
					curJob = null; // else retry - link is down
			} catch (InterruptedException | ExecutionException e) {
				VOSync.error("Error executing task: "+e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public void printQueue() {
		for(TransferJob job: listModel.values()) {
			logger.debug("Job: "+job.getDirection()+" "+job.getPath());
		}
	}
	
}
