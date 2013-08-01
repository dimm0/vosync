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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxServerException;

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
							curJobPath.toFile().getParentFile().mkdirs();
							File tempFile = File.createTempFile("."+curJobPath.getNodeName(), null, curJobPath.toFile().getParentFile());
							
							FileOutputStream outp = new FileOutputStream(tempFile);
							DropboxFileInfo info = VOSync.getInstance().getApi().getFile(curJobPath.getNodeStoragePath(), null, outp, null);
							outp.close();
							
							DirWatcher2.setIgnoreNode(curJobPath.toFile().getAbsoluteFile().toPath(), true);

							tempFile.renameTo(curJobPath.toFile());
							MetaHandler.setFile(curJobPath, curJobPath.toFile(), info.getMetadata().rev);

							DirWatcher2.setIgnoreNode(curJobPath.toFile().getAbsoluteFile().toPath(), false);
						} catch (DropboxServerException e) {
							e.printStackTrace();
							VOSync.error(e.reason);
							return true;
						} catch (IOException | DropboxIOException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return false;
						} catch (DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return true;
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
						} catch (DropboxServerException e) {
							e.printStackTrace();
							VOSync.error(e.reason);
							return true;
						} catch (IOException | DropboxIOException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return false;
						} catch (DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return true;
						}
	
						break;
					case pullDelete:
						try {
							Entry entMeta = VOSync.getInstance().getApi().metadata(curJobPath.getNodeStoragePath(), 0, null, false, null);
							if(!entMeta.isDir) {
								MetaHandler.delete(curJobPath);
								curJobPath.toFile().delete();
							} else {
								String[] filesList = curJobPath.toFile().list();
								if(null == filesList || curJobPath.toFile().isFile()) // is a file
									curJobPath.toFile().delete();
								else if(filesList.length == 0 || listModel.isEmpty()) // folder is empty or no more jobs - removing folder
									curJobPath.toFile().delete();
								else
									TaskController.addJob(curJob);
							}
						} catch (DropboxServerException e) {
							if(e.reason.equals("Not Found")) { // Already removed from storage
								MetaHandler.delete(curJobPath);
								curJobPath.toFile().delete();
							} else {
								e.printStackTrace();
								VOSync.error(e.reason);
								return true;
							}
						} catch (DropboxIOException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return false;
						} catch (DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return true;
						}
						break;
					case pushDelete:
						MetaHandler.delete(curJobPath);
						try {
							VOSync.getInstance().getApi().delete(curJobPath.getNodeStoragePath());
						} catch (DropboxServerException e) {
							e.printStackTrace();
							VOSync.error(e.reason);
							return true;
						} catch (DropboxIOException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return false;
						} catch (DropboxException e) {
							e.printStackTrace();
							VOSync.error(e.getMessage());
							return true;
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
