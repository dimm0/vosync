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
