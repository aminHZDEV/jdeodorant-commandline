package ast;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.Comment;

import java.util.List;

public class CommentObject {
	private ASTInformation comment;
	private final String text;
	private final CommentType type;
	private final int startLine;
	private final int endLine;
	private volatile int hashCode = 0;
	protected List<CommentObject> commentList;
	
	public CommentObject(String text, CommentType type, int startLine, int endLine) {
		this.text = text;
		this.type = type;
		this.startLine = startLine;
		this.endLine = endLine;
	}

	public void setComment(Comment comment) {
		this.comment = ASTInformationGenerator.generateASTInformation(comment);
	}

	public List<CommentObject> getComments() {
		return commentList;
	}

	public Comment getComment() {
		return (Comment)comment.recoverASTNode();
	}

	public String getText() {
		return text;
	}

	public CommentType getType() {
		return type;
	}
	//first line of the file starts from 0
	public int getStartLine() {
		return startLine;
	}

	public int getEndLine() {
		return endLine;
	}

	public ITypeRoot getITypeRoot() {
		return comment.getITypeRoot();
	}

	public int getStartPosition() {
		return comment.getStartPosition();
	}

	public int getLength() {
		return comment.getLength();
	}

    public boolean equals(Object o) {
        if(this == o) {
			return true;
		}

		if (o instanceof CommentObject comment) {

            return this.getITypeRoot().equals(comment.getITypeRoot()) && this.getStartPosition() == comment.getStartPosition() &&
				this.getLength() == comment.getLength();
		}
		return false;
    }

    public int hashCode() {
    	if(hashCode == 0) {
    		int result = 17;
    		result = 37*result + this.getITypeRoot().hashCode();
    		result = 37*result + this.getStartPosition();
    		result = 37*result + this.getLength();
    		hashCode = result;
    	}
    	return hashCode;
    }

	public String toString() {
        return comment.getITypeRoot().getPath() + "\n" +
                "Start line: " + startLine + "\n" +
                "End line:" + endLine + "\n" +
                text;
	}
}
