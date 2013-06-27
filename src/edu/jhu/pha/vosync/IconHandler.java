package edu.jhu.pha.vosync;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.apache.log4j.Logger;

public class IconHandler {
	
	private static final Logger logger = Logger.getLogger(IconHandler.class);
	
	static TrayIcon addIcon() {
		final PopupMenu popup = new PopupMenu();
		TrayIcon trayIcon = new TrayIcon(createImage("images/i-storage.png", "tray icon"), "VOSync", null);
        final SystemTray tray = SystemTray.getSystemTray();

        trayIcon.setImageAutoSize(true);

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(new ActionListener() {
            @Override
			public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        
        MenuItem syncFolderChooserItem = new MenuItem("Preferences...");
        syncFolderChooserItem.addActionListener(new ActionListener() {
            @Override
			public void actionPerformed(ActionEvent e) {
                SyncFolderChooser chooser = new SyncFolderChooser();
    			chooser.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                chooser.setVisible(true);
            }
        });
        
        popup.add(syncFolderChooserItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);
        
        try {
            tray.add(trayIcon);
            
            return trayIcon;
        } catch (AWTException e) {
            logger.error("TrayIcon could not be added.");
        }
        return null;
	}
	
    protected static Image createImage(String path, String description) {
        URL imageURL = IconHandler.class.getResource(path);
         
        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }
    

}
