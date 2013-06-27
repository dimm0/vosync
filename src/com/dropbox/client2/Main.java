package com.dropbox.client2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;

import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.dropbox.client2.session.WebAuthSession;

public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		WebAuthSession session = new WebAuthSession(new AppKeyPair("vosync","vosync_ssecret"), Session.AccessType.APP_FOLDER, new AccessTokenPair("ffa47e6b0d3ee12644676354062394fa","40a5eea36eb2464926d37e052afc8354"));
		//WebAuthSession session = new WebAuthSession(new AppKeyPair("sclient","ssecret"), Session.AccessType.DROPBOX, new AccessTokenPair("3ff044a71b3a275b3d2e056779511239","35d5203731804f4233a3eab472956a07"));
		DropboxAPI api = new DropboxAPI(session);
        //api.saveConfig("config/testing.json", false);
        //api.authenticateToken(api.getConfig().consumerKey, api.getConfig().consumerSecret, api.getConfig());
        try {
        	//System.out.println(Executors.newFixedThreadPool(1).getClass().getName());
        	
        	//Entry rootFolder = api.metadata("/", 0, null, true, null);
        	//System.out.println(rootFolder.hash);
			//System.out.println(api.createFolder("/123"));
			
        
        	
        	api.getFile("/dir1/build.xml", "0", System.out, null);
        	
			/*File inFile = new File("/Users/dmitry/OmniGrafflePro521.dmg");
			InputStream fInp = new FileInputStream(inFile);
			
			api.putFileOverwrite("/123/HddMonitor.zip", fInp, inFile.length(), null);
			
			fInp.close();
			fInp = new FileInputStream(inFile);
			
			
			api.putFile("/123/HddMonitor.zip", fInp, inFile.length(), null, null);
			fInp.close();*/
			
			//api.copy("/123/HddMonitor.zip", "/123/HddMonitor_copy.zip");
			//api.copy("/123/HddMonitor.zip", "/123/HddMonitor_copy2.zip");
			//api.move("/123/HddMonitor_copy2.zip", "/123/HddMonitor_move.zip");
			//api.delete("/123/HddMonitor.zip");
        	
        	//api.delete("/123");
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
