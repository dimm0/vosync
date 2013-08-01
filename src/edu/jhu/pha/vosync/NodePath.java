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

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.log4j.Logger;

public class NodePath {

	private static final Logger logger = Logger.getLogger(NodePath.class);
	
	private String[] pathTokens;
	
	private final char SEPARATOR = '/';

	private static FileSystem fileSystem = FileSystems.getDefault();
	
	public static Path startDir = null;
	
	public NodePath (String path) {
		if(null == path)
			path = "";
		this.pathTokens = StringUtils.split(path, SEPARATOR);
	}

	private NodePath(String[] pathElms) {
		this.pathTokens = pathElms;
	}

	public NodePath (Path path) {
		this("/"+((startDir)).relativize(path).toString().replaceAll("\\\\", "/"));
	}

	public NodePath append(NodePath newPath) {
		return new NodePath((String[]) ArrayUtils.addAll(pathTokens, newPath.getNodeStoragePathArray()));
	}

	public String getNodeName() {
		if(pathTokens.length == 0)
			return "";
		return pathTokens[pathTokens.length-1];
	}
	
	/**
	 * Return the path to the node considering the appContainer parameter
	 * @return
	 */
	public String getNodeOuterPath() {
		if(pathTokens.length == 0)
			return "";

		return SEPARATOR+StringUtils.join(pathTokens, SEPARATOR,1,pathTokens.length);
	}
	
	/**
	 * Returns the node full path
	 * @return
	 */
	public String getNodeStoragePath() {
		if(pathTokens.length == 0)
			return "";
		return SEPARATOR+StringUtils.join(pathTokens, SEPARATOR);
	}

	/**
	 * returns the path in the filesystem with startPath
	 * @param pathStr
	 * @return
	 */
	public Path getNodeFilesystemPath() {
		return fileSystem.getPath(startDir.toString(), this.getNodeStoragePath());
	}
	
	public String[] getNodeStoragePathArray() {
		return pathTokens;
	}
	
	public File toFile() {
		return getNodeFilesystemPath().toFile();
	}
	
	public NodePath getParentPath() {
		return new NodePath((String[])ArrayUtils.subarray(this.pathTokens, 0, this.pathTokens.length-1));
	}

	/**
	 * Path points to the root container
	 * @param considerAppContainer the application sandbox is used
	 * @return
	 */
	public boolean isRoot(boolean considerAppContainer) {
		return getNodeStoragePathArray().length == 0;
	}
	
	/**
	 * Checks if the path in parameter is parent of current path
	 * @param checkPath the path to check
	 * @return true if current path starts with checkPath
	 */
	public boolean isParent(NodePath checkPath) {
		if(checkPath.pathTokens.length > this.pathTokens.length)
			return false;
		
		for(int i=0; i< checkPath.pathTokens.length; i++){
			if(!this.pathTokens[i].equals(checkPath.pathTokens[i]))
				return false;
		}
		
		return true;
	}
	
	public int hashCode() {
        return new HashCodeBuilder(45, 67).
            append(pathTokens).
            toHashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(obj instanceof NodePath))
            return false;

        NodePath np2 = (NodePath) obj;
        return new EqualsBuilder().
            append(this.pathTokens, np2.pathTokens).
            isEquals();
    }
    
    public String toString() {
    	return this.getNodeStoragePath();
    }
}
