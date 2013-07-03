package edu.jhu.pha.vosync;

public class TransferJob {
	public enum JobResult {success, error};
	public enum Direction {pushContent, pullContent, pushDelete, pullDelete};

	private NodePath path;
	private Direction dir;
	
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
}
