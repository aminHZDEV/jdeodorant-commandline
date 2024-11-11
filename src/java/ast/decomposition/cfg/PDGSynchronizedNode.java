package java.ast.decomposition.cfg;

import java.util.Set;

import java.ast.FieldObject;
import java.ast.VariableDeclarationObject;

public class PDGSynchronizedNode extends PDGBlockNode {
	public PDGSynchronizedNode(CFGSynchronizedNode cfgSynchronizedNode, Set<VariableDeclarationObject> variableDeclarationsInMethod,
			Set<FieldObject> fieldsAccessedInMethod) {
		super(cfgSynchronizedNode, variableDeclarationsInMethod, fieldsAccessedInMethod);
		this.controlParent = cfgSynchronizedNode.getControlParent();
		determineDefinedAndUsedVariables();
	}
}
