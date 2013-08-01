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

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JDialog;

import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Account;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.dropbox.client2.session.WebAuthSession;

public class VOSync {

	/**
	 * Logger class
	 */
	private static final Logger logger = Logger.getLogger(VOSync.class);
	
	/**
	 * User preferences
	 */
	private static final Preferences prefs = Preferences.userRoot().node(VOSync.class.getName());

	/**
	 * Program main class instance singleton
	 */
	private static VOSync instance;
	
	/**
	 * System tray icon
	 */
	private static TrayIcon trayIcon;
	
	/**
	 * API object for accessing the dropbox REST service
	 */
	private DropboxAPI<WebAuthSession> api;
	
	/**
	 * Dropbox object for OAuth authentication
	 */
	private static WebAuthSession session;
	
	/**
	 * Init the session. Can clear the access token for preferences to get the new token.
	 * @param clearAccessToken Clear the current Access Token
	 */
	static void initSession(boolean clearAccessToken) {
		AccessTokenPair tokenPair = null;
		
		if(!clearAccessToken && (null != prefs.get("oauthToken", null) && null != prefs.get("oauthSecret", null)))
			tokenPair = new AccessTokenPair(prefs.get("oauthToken", null), prefs.get("oauthSecret", null));
		
		session = new WebAuthSession(new AppKeyPair("vosync","vosync_ssecret"), Session.AccessType.APP_FOLDER, tokenPair);
	}


	
	/**
	 * Sync directory watcher thread instance
	 */
	private DirWatcher2 watcherThread;
	
	/**
	 * Application is initialized
	 */
	private boolean init = false;
	
	//private static String serviceUrl = "http://zinc27.pha.jhu.edu/vospace-2.0";
//	private static String serviceUrl = "http://vospace.sdsc.edu:8080/vospace-2.0";
	private static String serviceUrl = null;
	
	/**
	 * Print or display debug message
	 * @param debug The debug message text
	 */
	public static void debug(String debug) {
		if(null != trayIcon) {
			trayIcon.displayMessage("", debug, MessageType.INFO);
		}
		
		logger.debug(debug);
	}
	
	/**
	 * Print or display error message
	 * @param error Error message text
	 */
	public static void error(String error) {
		if(null != trayIcon)
			trayIcon.displayMessage("Error", error, MessageType.ERROR);

		logger.error(error);
	}
	
	/**
	 * Returns main program instance singleton
	 * @return
	 */
	public static VOSync getInstance() {
		if(null == instance)
			instance = new VOSync();
		return instance;
	}
	
	/**
	 * Returns preferences
	 * @return
	 */
	public static Preferences getPrefs() {
		return prefs;
	}
	
	public static String getServiceUrl() {
		return serviceUrl;
	}

	public static WebAuthSession getSession() {
		return session;
	}

	public static void main(String[] s) throws BackingStoreException {
		VOSync cloud = getInstance();
		cloud.init();
	}
	
	static void setCredentials(String oauthToken, String oauthSecret) {
		prefs.put("oauthToken", oauthToken);
		prefs.put("oauthSecret", oauthSecret);
		try {
			prefs.flush();
		} catch (BackingStoreException e1) {
			error("Failed to save the preferences");
		}
	}

	static void setSyncDir(String syncDir) {
		prefs.put("syncdir", syncDir);
		try {
			prefs.flush();
		} catch (BackingStoreException e1) {
			error("Failed to save the preferences");
		}
	}

	private VOSync() {
		if (SystemTray.isSupported()) {
			trayIcon = IconHandler.addIcon();
		}
		Properties prop = new Properties();
		try(InputStream inp = getClass().getResourceAsStream("/vosync.properties")) {
			prop.load(inp);
			serviceUrl = (String)prop.get("serviceUrl");
			logger.debug(serviceUrl);
		} catch(IOException ex) {
			ex.printStackTrace();
		}
		TaskController.getInstance();
		initSession(false);
	}
	
	public DropboxAPI<WebAuthSession> getApi() {
		return api;
	}

	public DirWatcher2 getWatcher() {
		return watcherThread;
	}

	protected void init() {
		try {
			if(null == prefs.get("syncdir", null) || null == prefs.get("oauthToken", null) || null == prefs.get("oauthSecret", null)) {
	            SyncFolderChooser chooser = new SyncFolderChooser();
				chooser.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	            chooser.setVisible(true);
	        } else {
	    		api = new DropboxAPI<WebAuthSession>(session);

				Account account = api.accountInfo();

	        	String syncDir = prefs.get("syncdir", null);
	        	NodePath.startDir = (new File(syncDir)).toPath(); // set static NodePath parameter to use for paths conversion
				Path syncPath = Paths.get(syncDir);

				getInstance().runWatcher(syncPath);

				NodesSynchronizer.syncPath(new NodePath("/"));

				new EventListener(getServiceUrl(), account.displayName);
				
				TaskController.getInstance().start();
				
				init = true;
			}
		} catch(DropboxIOException ex) {
			error("Error connecting to the server.");
		} catch(DropboxException ex) {
			error("Error: "+ex.getMessage());
		}
		
	}

	public void runWatcher(Path syncPath) {
		try {
			logger.debug("Running watcher");
			if(null != watcherThread) {
				synchronized(watcherThread) {
					watcherThread.interrupt();
				}
				watcherThread.join(10000);
			}
			logger.debug("Running watcher: after instance check");
			watcherThread = new DirWatcher2(syncPath, api);
			watcherThread.setDaemon(true);
			watcherThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
