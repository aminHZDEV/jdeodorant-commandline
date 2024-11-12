package main.java.ast.decomposition.cfg;

import main.java.ast.decomposition.AbstractStatement;

public class CFGBranchSwitchNode extends CFGBranchConditionalNode {

	public CFGBranchSwitchNode(AbstractStatement statement) {
		super(statement);
	}
}
