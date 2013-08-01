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

import java.io.IOException;
import java.net.URLDecoder;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.xlightweb.Event;
import org.xlightweb.IEventDataSource;
import org.xlightweb.IEventHandler;
import org.xlightweb.client.HttpClient;

import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;

import edu.jhu.pha.vosync.TransferJob.Direction;

public class EventListener {

	private static final Logger logger = Logger.getLogger(EventListener.class);

	private HttpClient httpClient = new HttpClient();
	private IEventDataSource eventSource;
	
	public EventListener(String serviceUrl, String account) {
		try {
			eventSource	= httpClient.openEventDataSource("http://"+VOSync.getServiceUrl()+"/updates?user="+account+"&path=/vosync/*", new MyEventHandler());
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public void destroy() {
		try {
			eventSource.close();
		} catch(Exception ex) {}
		try {
			httpClient.close();
		} catch(Exception ex) {}
	}

	private class MyEventHandler implements IEventHandler {

		public void onConnect(IEventDataSource webEventDataSource) throws IOException {
			logger.debug("Connected eventlistener");
		}

		public void onMessage(IEventDataSource webEventDataSource) throws IOException {
			Event event = webEventDataSource.readMessage();
			VOSync.debug("Event "+event.getData());
			JSONObject obj= (JSONObject)JSONValue.parse(event.getData());
//			NodePath containerChanged = new NodePath((String)obj.get("container"));
			try {
				String pathStr = URLDecoder.decode(StringUtils.substringAfter((String)obj.get("uri"), "!vospace"), "UTF-8");
				NodePath serviceNodeChangedPath = new NodePath(pathStr);
				NodePath nodeChangedPath = new NodePath(serviceNodeChangedPath.getNodeOuterPath());
				Entry ent = VOSync.getInstance().getApi().metadata(nodeChangedPath.getNodeStoragePath(), 0, null, true, null);
				if(!ent.isDir){
					boolean isStoredLocally = MetaHandler.isStored(nodeChangedPath);
					logger.debug("Is stored locally: "+isStoredLocally+" "+nodeChangedPath.getNodeStoragePath());
					if(!isStoredLocally && !ent.isDeleted){
						TransferJob job = new TransferJob(Direction.pullContent, nodeChangedPath);
						TaskController.addJob(job);
					} else if (isStoredLocally) { 
						if(ent.isDeleted) { // remove local file - deleted remotely
							logger.debug("Creating delete job for: "+nodeChangedPath+" "+ent.rev);
							TransferJob job = new TransferJob(Direction.pullDelete, nodeChangedPath);
							TaskController.addJob(job);
						} else if(!MetaHandler.isCurrent(nodeChangedPath, ent.rev)) {
							logger.debug("Not current: "+nodeChangedPath+" "+ent.rev);
							VOSync.debug("Creating new file pulled from event: "+nodeChangedPath.getNodeName());
							TransferJob job = new TransferJob(Direction.pullContent, nodeChangedPath);
							TaskController.addJob(job);
						}
					}
				} else {
					if(ent.isDeleted) {
						TransferJob job = new TransferJob(Direction.pullDelete, nodeChangedPath);
						TaskController.addJob(job);
					} else {
						nodeChangedPath.toFile().mkdirs();
					}
				}
			} catch(DropboxException ex) {
				VOSync.error(ex.getMessage());
				ex.printStackTrace();
			}
		}

		public void onDisconnect(IEventDataSource webEventDataSource) throws IOException {
			logger.debug("Disconnected eventlistener");
		}            
	}

}
