package edu.jhu.pha.vosync;

import java.io.IOException;

import com.dropbox.client2.DropboxAPI.Entry;

public abstract class DropboxEntryVisitor {
	public void preVisitDirectory(Entry entry) throws IOException {};
	public void visitFile(Entry entry) {};
	public void postVisitDirectory(Entry entry) {};
}
