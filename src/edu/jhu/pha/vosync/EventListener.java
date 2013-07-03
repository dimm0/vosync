package edu.jhu.pha.vosync;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.xlightweb.Event;
import org.xlightweb.IEventDataSource;
import org.xlightweb.IEventHandler;
import org.xlightweb.client.HttpClient;

import com.dropbox.client2.exception.DropboxException;

public class EventListener {

	private static final Logger logger = Logger.getLogger(EventListener.class);

	private HttpClient httpClient = new HttpClient();
	private IEventDataSource eventSource;
	
	public void init() {
		try {
			eventSource	= httpClient.openEventDataSource("http://localhost/vobox/updates?user=https://sso.usvao.org/openid/id/dimm&path=/vosync/*", new MyEventHandler());
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
		   logger.debug("Event "+event.getData());
		   JSONObject obj= (JSONObject)JSONValue.parse(event.getData());
		   String containerChanged = (String)obj.get("container");
		   ContainerNode cont = new ContainerNode(new NodePath(containerChanged));
		   try {
				cont.sync();
		   } catch (DropboxException e) {
			   e.printStackTrace();
		   }
	   }
	        
	   public void onDisconnect(IEventDataSource webEventDataSource) throws IOException {
		   logger.debug("Disconnected eventlistener");
	   }            
	}

}
