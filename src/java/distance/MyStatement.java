package java.distance;

import java.ast.decomposition.AbstractStatement;

public class MyStatement extends MyAbstractStatement {

	public MyStatement(AbstractStatement statement) {
		super(statement);
	}

	public MyStatement(MyMethodInvocation methodInvocation) {
		super(methodInvocation);
	}
}
