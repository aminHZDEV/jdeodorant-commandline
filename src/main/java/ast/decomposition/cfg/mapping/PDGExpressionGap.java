package ast.decomposition.cfg.mapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ast.decomposition.AbstractExpression;
import ast.decomposition.cfg.PDGNode;
import ast.decomposition.matching.ASTNodeDifference;
import ast.decomposition.matching.FieldAssignmentReplacedWithSetterInvocationDifference;
import ast.util.ExpressionExtractor;
import ast.util.ThrownExceptionVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;

public class PDGExpressionGap extends Gap {
	private ASTNodeDifference difference;
	
	public PDGExpressionGap(ASTNodeDifference difference) {
		this.difference = difference;
	}

	public ASTNodeDifference getASTNodeDifference() {
		return difference;
	}

	public Set<IVariableBinding> getUsedVariableBindingsG1() {
		return getUsedVariableBindings(difference.getExpression1());
	}

	public Set<IVariableBinding> getUsedVariableBindingsG2() {
		return getUsedVariableBindings(difference.getExpression2());
	}
 
	public boolean variableIsDefinedAndUsedInBlockGap(VariableBindingPair pair, Set<PDGNode> mappedNodesG1, Set<PDGNode> mappedNodesG2) {
		return variableDefinedInNodes(mappedNodesG1, pair.getBinding1()) && variableDefinedInNodes(mappedNodesG2, pair.getBinding2()) &&
				variableUsedInNodes(mappedNodesG1, pair.getBinding1()) && variableUsedInNodes(mappedNodesG2, pair.getBinding2());
	}

	private Set<IVariableBinding> getUsedVariableBindings(AbstractExpression expression) {
		Expression expr = ASTNodeDifference.getParentExpressionOfMethodNameOrTypeName(expression.getExpression());
		ExpressionExtractor expressionExtractor = new ExpressionExtractor();
		List<Expression> localVariableInstructions = expressionExtractor.getVariableInstructions(expr);
		Set<IVariableBinding> usedVariableBindings = new LinkedHashSet<IVariableBinding>();
		for(Expression variableInstruction : localVariableInstructions) {
			SimpleName simpleName = (SimpleName)variableInstruction;
			IBinding binding = simpleName.resolveBinding();
			if(binding != null && binding.getKind() == IBinding.VARIABLE) {
				IVariableBinding variableBinding = (IVariableBinding) binding;
				if(!variableBinding.isField() && !simpleName.isDeclaration())
					usedVariableBindings.add(variableBinding);
			}
		}
		return usedVariableBindings;
	}

	public ITypeBinding getReturnType() {
		ITypeBinding typeBinding1 = ASTNodeDifference.getParentExpressionOfMethodNameOrTypeName(difference.getExpression1().getExpression()).resolveTypeBinding();
		ITypeBinding typeBinding2 = ASTNodeDifference.getParentExpressionOfMethodNameOrTypeName(difference.getExpression2().getExpression()).resolveTypeBinding();
		if(difference instanceof FieldAssignmentReplacedWithSetterInvocationDifference) {
			if(typeBinding1.getQualifiedName().equals("void")) {
				return typeBinding1;
			}
			if(typeBinding2.getQualifiedName().equals("void")) {
				return typeBinding2;
			}
		}
		if(typeBinding1 != null && typeBinding2 != null) {
			if(typeBinding1.getQualifiedName().equals("null") && !typeBinding2.getQualifiedName().equals("null")) {
				return typeBinding2;
			}
			else if(typeBinding2.getQualifiedName().equals("null") && !typeBinding1.getQualifiedName().equals("null")) {
				return typeBinding1;
			}
			else {
				return PreconditionExaminer.determineType(typeBinding1, typeBinding2);
			}
		}
		else if(typeBinding1 == null && typeBinding2 != null) {
			return typeBinding2;
		}
		else if(typeBinding2 == null && typeBinding1 != null) {
			return typeBinding1;
		}
		return null;
	}

	public Set<ITypeBinding> getThrownExceptions() {
		Set<ITypeBinding> thrownExceptionTypeBindings = new LinkedHashSet<ITypeBinding>();
		Expression expr1 = ASTNodeDifference.getParentExpressionOfMethodNameOrTypeName(difference.getExpression1().getExpression());
		Expression expr2 = ASTNodeDifference.getParentExpressionOfMethodNameOrTypeName(difference.getExpression2().getExpression());
		ThrownExceptionVisitor thrownExceptionVisitor = new ThrownExceptionVisitor();
		expr1.accept(thrownExceptionVisitor);
		for(ITypeBinding typeBinding : thrownExceptionVisitor.getTypeBindings()) {
			addTypeBinding(typeBinding, thrownExceptionTypeBindings);
		}
		thrownExceptionVisitor = new ThrownExceptionVisitor();
		expr2.accept(thrownExceptionVisitor);
		for(ITypeBinding typeBinding : thrownExceptionVisitor.getTypeBindings()) {
			addTypeBinding(typeBinding, thrownExceptionTypeBindings);
		}
		return thrownExceptionTypeBindings;
	}

	@Override
	public Set<IMethodBinding> getAllMethodsInvokedThroughVariable(VariableBindingPair variableBindingPair) {
		Set<IMethodBinding> methods = new LinkedHashSet<IMethodBinding>();
		methods.addAll(getAllMethodsInvokedThroughVariable(difference.getExpression1(), variableBindingPair.getBinding1()));
		methods.addAll(getAllMethodsInvokedThroughVariable(difference.getExpression2(), variableBindingPair.getBinding2()));
		return methods;
	}
}
