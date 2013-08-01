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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.log4j.Logger;

public class DbPool {
	
	private static final Logger logger = Logger.getLogger(DbPool.class);
    //private static String framework = "embedded";
    //private static String driver = "org.apache.derby.jdbc.EmbeddedDriver";
//    private static String protocol = "jdbc:derby:";
    private static String protocol = "jdbc:sqlite:";
    //private static String protocol = "jdbc:derby://localhost:1527/";

    static {
    	try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
    	DbPool.goSql("Create files if needed",
        		"create table if not exists FILES(name varchar(256), rev int, mtime bigint, hash char(24), PRIMARY KEY (name))",
                new SqlWorker<Boolean>() {
                    @Override
                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
                    	DatabaseMetaData dbmd = conn.getMetaData();
                    	ResultSet rs = dbmd.getTables(null, "APP", "FILES", new String[] {"TABLE"});
                    	if(!rs.next()) {
                    		return stmt.execute();
                    	}
                    	return true;
                    }
                }
        );
    }
    
    /** Helper class for goSql() */
    public static abstract class SqlWorker<T> {
        abstract public T go(Connection conn, PreparedStatement stmt) throws SQLException;
        public void error(String context, SQLException e) { logger.warn(context, e); }
    }

    /** Helper function to setup and teardown SQL connection & statement. 
     * @throws ClassNotFoundException 
     * @throws IllegalAccessException 
     * @throws InstantiationException */
    public static <T> T goSql(String context, String sql, SqlWorker<T> goer) {
        Properties props = new Properties(); // connection properties
    	//logger.debug("Doing SQL: "+context + " "+sql);
    	String dbName = "vosync.db"; // the name of the database
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
//            conn = DriverManager.getConnection(protocol + dbName + ";create=true", props);
            conn = DriverManager.getConnection(protocol + dbName, props);
            if (sql != null)
                stmt = conn.prepareStatement(sql);
            return goer.go(conn, stmt);
        } catch (SQLException e) {
            goer.error(context, e);
            return null;
        } finally {
            close(stmt);
            close(conn);
        }
    }

    public static void close(Connection c) { if (c != null) { try { c.close(); } catch(Exception ignored) {} } }
    public static void close(Statement s) { if (s != null) { try { s.close(); } catch(Exception ignored) {} } }
    public static void close(InputStream in) { if (in != null) { try { in.close(); } catch(Exception ignored) {} } }

}
