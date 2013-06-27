/*******************************************************************************
 * Copyright (c) 2011, Johns Hopkins University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Johns Hopkins University nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Johns Hopkins University BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    private static String protocol = "jdbc:derby:";
    //private static String protocol = "jdbc:derby://localhost:1527/";

    static {
    	DbPool.goSql("Create files if needed",
        		"create table FILES(name varchar(256), rev int, mtime bigint, hash char(24), PRIMARY KEY (name))",
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
    	String dbName = "vosyncDB"; // the name of the database
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DriverManager.getConnection(protocol + dbName + ";create=true", props);
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
