package edu.jhu.pha.vosync;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import edu.jhu.pha.vosync.DbPool.SqlWorker;

public class MetaHandler {
	
	private static final Logger logger = Logger.getLogger(MetaHandler.class);
	
	public static boolean delete(final NodePath filePath) {
    	return DbPool.goSql("Delete record",
        		"delete from FILES where name like ? or name = ?",
                new SqlWorker<Boolean>() {
                    @Override
                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	stmt.setString(1, filePath.getNodeStoragePath()+"/%");
                    	stmt.setString(2, filePath.getNodeStoragePath());
                    	return stmt.executeUpdate() > 0;
                    }
                }
        );
	}
	
	public static String getHash(final String filePath) {
    	return DbPool.goSql("Get file revision",
        		"select hash from FILES where name = ?",
                new SqlWorker<String>() {
                    @Override
                    public String go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	stmt.setString(1, filePath);
                    	ResultSet resSet = stmt.executeQuery();
                    	if(resSet.next()){
                    		return resSet.getString(1);
                    	} else {
                    		return null;
                    	}
                    }
                }
        );
	}

	public static String getRev(final String filePath) {
    	return DbPool.goSql("Get file revision",
        		"select rev from FILES where name = ?",
                new SqlWorker<String>() {
                    @Override
                    public String go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	stmt.setString(1, filePath);
                    	ResultSet resSet = stmt.executeQuery();
                    	if(resSet.next()){
                    		return resSet.getString(1);
                    	} else {
                    		return "0";
                    	}
                    }
                }
        );
	}

	/**
	 * Method to check if the local database record reflects the changes in local file or folder
	 * @param filePath
	 * @param file
	 * @return
	 */
	public static boolean isModified(final NodePath filePath, final File file) {
    	return DbPool.goSql("Check if file is modified",
        		"select mtime from FILES where name = ?",
                new SqlWorker<Boolean>() {
                    @Override
                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	stmt.setString(1, filePath.getNodeStoragePath());
                    	ResultSet resSet = stmt.executeQuery();
                    	if(resSet.next()){
                    		return resSet.getLong(1) != file.lastModified();
                    	} else {// not found
                    		return true;
                    	}
                    }
                }
        );
	}

	public static boolean isStored(final NodePath path) {
    	return DbPool.goSql("Check if stored",
        		"select count(*) from FILES where name = ?",
                new SqlWorker<Boolean>() {
                    @Override
                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	stmt.setString(1, path.getNodeStoragePath());
                    	ResultSet resSet = stmt.executeQuery();
                    	if(resSet.next()){
                    		return resSet.getInt(1) > 0;
                    	} else {//can't happen
                    		return false;
                    	}
                    }
                }
        );
	}
	
	
	
	
	
	/**
	 * Method to check if the remote database record matches the local record
	 * @param filePath
	 * @param file
	 * @return
	 */
	public static boolean isCurrent(final String filePath, final String rev) {
    	return DbPool.goSql("Check if remote file is modified",
        		"select rev from FILES where name = ?",
                new SqlWorker<Boolean>() {
                    @Override
                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	stmt.setString(1, filePath);
                    	ResultSet resSet = stmt.executeQuery();
                    	if(resSet.next()){
                    		return resSet.getString(1).equals(rev);
                    	} else {// not found
                    		return false;
                    	}
                    }
                }
        );
	}

	public static void setFile(final NodePath path, final File file, final String rev) {
		logger.debug("Seting file "+path.getNodeStoragePath()+" at rev "+rev);
		if(!isStored(path)) {
			logger.debug("Adding File "+path.getNodeStoragePath()+" to DB");
	    	DbPool.goSql("Set file",
	        		"insert into FILES(name, rev, mtime) values (?, ?, ?)",
	                new SqlWorker<Boolean>() {
	                    @Override
	                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
	                    	stmt.setString(1, path.getNodeStoragePath());
	                    	stmt.setString(2, rev);
	                    	stmt.setLong(3, file.lastModified());
	                    	return stmt.executeUpdate() > 0;
	                    }
	                }
	        );
		} else {
			logger.debug("Updating File "+path.getNodeStoragePath()+" in DB");
	    	DbPool.goSql("Update file",
	        		"update FILES set rev = ?, mtime = ? where name = ?",
	                new SqlWorker<Boolean>() {
	                    @Override
	                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
	                    	stmt.setString(1, rev);
	                    	stmt.setLong(2, file.lastModified());
	                    	stmt.setString(3, path.getNodeStoragePath());
	                    	return stmt.executeUpdate() > 0;
	                    }
	                }
	        );
		}
	}
}
