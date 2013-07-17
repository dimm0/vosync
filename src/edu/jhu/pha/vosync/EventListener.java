package edu.jhu.pha.vosync;

import java.io.IOException;

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

	public void init() {
		try {
			eventSource	= httpClient.openEventDataSource("http://localhost/vobox/updates?user=https://sso.usvao.org/openid/id/dimm&path=/vosync", new MyEventHandler());
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
			NodePath containerChanged = new NodePath((String)obj.get("container"));

			try {
				Entry dirEntry = VOSync.getInstance().getApi().metadata(containerChanged.getNodeOuterPath(), 0, null, true, null);
				for(Entry ent: dirEntry.contents) {
					NodePath curPath = new NodePath(ent.path);
					if(!ent.isDir){
						boolean isStoredLocally = MetaHandler.isStored(curPath);
						if(!isStoredLocally && !ent.isDeleted){
							TransferJob job = new TransferJob(Direction.pullContent, curPath);
							TaskController.addJob(job);
						} else if (isStoredLocally) { 
							if(ent.isDeleted) { // remove local file - deleted remotely
								TransferJob job = new TransferJob(Direction.pullDelete, curPath);
								TaskController.addJob(job);
							} else if(!MetaHandler.isCurrent(curPath, ent.rev)) {
								TransferJob job = new TransferJob(Direction.pullContent, curPath);
								TaskController.addJob(job);
							}
						}
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
