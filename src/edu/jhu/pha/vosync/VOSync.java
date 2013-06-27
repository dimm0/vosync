package edu.jhu.pha.vosync;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JDialog;

import org.apache.log4j.Logger;

import com.dropbox.client2.DropboxAPI;
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
	 * Sync directory watcher thread instance
	 */
	private DirWatcher2 watcherThread;
	
	/**
	 * Application is initialized
	 */
	private boolean init = false;
	
	//private static String serviceUrl = "http://zinc27.pha.jhu.edu/vospace-2.0";
//	private static String serviceUrl = "http://vospace.sdsc.edu:8080/vospace-2.0";
	private static String serviceUrl = "http://localhost:8080/vospace-2.0";
	
	/**
	 * Print or display debug message
	 * @param debug The debug message text
	 */
	public static void debug(String debug) {
		if(null != trayIcon) {
			trayIcon.displayMessage("", debug, MessageType.INFO);
		} else
			logger.debug(debug);
	}
	
	/**
	 * Print or display error message
	 * @param error Error message text
	 */
	public static void error(String error) {
		if(null != trayIcon)
			trayIcon.displayMessage("Error", error, MessageType.ERROR);
		else
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
	
	public static void main(String[] s) throws BackingStoreException {
		VOSync cloud = getInstance();
		cloud.init();
	}
	
	private VOSync() {
		if (SystemTray.isSupported()) {
			trayIcon = IconHandler.addIcon();
		}
		
		initSession(false);

	}

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

	protected void init() {
		try {
			if(null == prefs.get("syncdir", null) || null == prefs.get("oauthToken", null) || null == prefs.get("oauthSecret", null)) {
	            SyncFolderChooser chooser = new SyncFolderChooser();
				chooser.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	            chooser.setVisible(true);
	        } else {
	    		//WebAuthSession session = new WebAuthSession(new AppKeyPair("vosync","vosync_ssecret"), Session.AccessType.APP_FOLDER, new AccessTokenPair("ffa47e6b0d3ee12644676354062394fa","40a5eea36eb2464926d37e052afc8354"));
	    		api = new DropboxAPI<WebAuthSession>(session);

				api.accountInfo();

	        	String syncDir = prefs.get("syncdir", null);
				Path syncPath = Paths.get(syncDir);
				getInstance().runWatcher(syncPath);
			}
			
			init = true;
		} catch(DropboxIOException ex) {
			this.error("Error connecting to the server.");
		} catch(DropboxException ex) {
			this.error("Error: "+ex.getMessage());
		}
		
	}
	
	public DropboxAPI<WebAuthSession> getApi() {
		return api;
	}

	public void runWatcher(Path syncPath) {
		try {
			if(null != watcherThread) {
				synchronized(watcherThread) {
					watcherThread.interrupt();
				}
				watcherThread.join(10000);
			}
			watcherThread = new DirWatcher2(syncPath, api);
			watcherThread.setDaemon(true);
			watcherThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
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

	public static String getServiceUrl() {
		return serviceUrl;
	}

	public static WebAuthSession getSession() {
		return session;
	}
	
}
