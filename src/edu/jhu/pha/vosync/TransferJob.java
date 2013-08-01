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

public class TransferJob {
	public enum JobStatus {idle, running, success, error};
	public enum Direction {pushContent, pullContent, pushDelete, pullDelete};

	private NodePath path;
	private Direction dir;
	private JobStatus status = JobStatus.idle;
	
	public TransferJob(Direction dir, NodePath path) {
		this.dir = dir;
		this.path = path;
	}
	
	public NodePath getPath() {
		return this.path;
	}
	
	public Direction getDirection() {
		return this.dir;
	}
	
	public String toString() {
		return this.getDirection()+"   "+this.status+"   "+this.getPath();
	}
	
	public void setJobStatus(JobStatus status) {
		this.status = status;
	}
}
