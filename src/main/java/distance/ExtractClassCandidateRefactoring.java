package distance;

import ast.FieldObject;
import ast.MethodObject;
import ast.TypeObject;
import ast.util.TopicFinder;
import ast.visualization.GodClassVisualizationData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jface.text.Position;

public class ExtractClassCandidateRefactoring extends CandidateRefactoring implements Comparable<ExtractClassCandidateRefactoring> {

	private final MySystem system;
	private final MyClass sourceClass;
	private final List<Entity> extractedEntities;
	private final Map<MyMethod, Boolean> leaveDelegate;
	private String targetClassName;
	private GodClassVisualizationData visualizationData;
	private Integer userRate;
	private List<String> topics;

	public ExtractClassCandidateRefactoring(MySystem system, MyClass sourceClass, ArrayList<Entity> extractedEntities) {
		super();
		this.system = system;
		this.sourceClass = sourceClass;
		this.extractedEntities = extractedEntities;
		this.leaveDelegate = new LinkedHashMap<MyMethod, Boolean>();
		if (system.getClass(sourceClass.getName() + "Product") == null) {
			this.targetClassName = sourceClass.getName() + "Product";
		}
		else {
			this.targetClassName = sourceClass.getName() + "Product2";
		}
		this.topics = new ArrayList<String>();
		Set<MethodObject> extractedMethods = new LinkedHashSet<MethodObject>();
		Set<FieldObject> extractedFields = new LinkedHashSet<FieldObject>();
		for(Entity entity : extractedEntities) {
			if(entity instanceof MyMethod myMethod) {
                extractedMethods.add(myMethod.getMethodObject());
			}
			else if(entity instanceof MyAttribute myAttribute) {
                extractedFields.add(myAttribute.getFieldObject());
			}
		}
//		this.visualizationData = new GodClassVisualizationData(sourceClass.getClassObject(), extractedMethods, extractedFields);
	}

	public String getTargetClassName() {
		return targetClassName;
	}

	public void setTargetClassName(String targetClassName) {
		this.targetClassName = targetClassName;
	}

	public List<Entity> getExtractedEntities() {
		return extractedEntities;
	}

	public Set<MethodDeclaration> getExtractedMethods() {
		Set<MethodDeclaration> extractedMethods = new LinkedHashSet<MethodDeclaration>();
		for(Entity entity : extractedEntities) {
			if(entity instanceof MyMethod method) {
                extractedMethods.add(method.getMethodObject().getMethodDeclaration());
			}
		}
		return extractedMethods;
	}

	public Set<MethodDeclaration> getDelegateMethods() {
		Set<MethodDeclaration> delegateMethods = new LinkedHashSet<MethodDeclaration>();
		for(MyMethod method : leaveDelegate.keySet()) {
			if(leaveDelegate.get(method))
				delegateMethods.add(method.getMethodObject().getMethodDeclaration());
		}
		return delegateMethods;
	}

	public Set<VariableDeclaration> getExtractedFieldFragments() {
		Set<VariableDeclaration> extractedFieldFragments = new LinkedHashSet<VariableDeclaration>();
		for(Entity entity : extractedEntities) {
			if(entity instanceof MyAttribute attribute) {
                extractedFieldFragments.add(attribute.getFieldObject().getVariableDeclaration());
			}
		}
		return extractedFieldFragments;
	}

	public Map<MyMethod, Boolean> getLeaveDelegate() {
		return leaveDelegate;
	}

	public boolean leaveDelegate(MyMethod method) {
		return system.getSystemObject().containsMethodInvocation(method.getMethodObject().generateMethodInvocation(), sourceClass.getClassObject()) ||
		system.getSystemObject().containsSuperMethodInvocation(method.getMethodObject().generateSuperMethodInvocation());
	}

	public boolean isApplicable() {
		int methodCounter = 0;
		for (Entity entity : extractedEntities) {
			if(entity instanceof MyMethod method) {
                methodCounter++;
				if (isSynchronized(method) || containsSuperMethodInvocation(method) ||
						overridesMethod(method) || method.isAbstract() || containsFieldAccessOfEnclosingClass(method) ||
						isReadObject(method) || isWriteObject(method))
					return false;
			}
			else if(entity instanceof MyAttribute attribute) {
                if(!attribute.getAccess().equals("private")) {
					if(system.getSystemObject().containsFieldInstruction(attribute.getFieldObject().generateFieldInstruction(), sourceClass.getClassObject()))
						return false;
				}
			}
		}
        return extractedEntities.size() > 2 && methodCounter != 0 && validRemainingMethodsInSourceClass() && validRemainingFieldsInSourceClass() && !visualizationData.containsNonAccessedFieldInExtractedClass();
	}

	private boolean validRemainingMethodsInSourceClass() {
		for(MyMethod sourceMethod : sourceClass.getMethodList()) {
			if(!extractedEntities.contains(sourceMethod)) {
				MethodObject methodObject = sourceMethod.getMethodObject();
				if(!methodObject.isStatic() && !methodObject.isAbstract() && methodObject.isGetter() == null && methodObject.isSetter() == null && methodObject.isDelegate() == null &&
						!isReadObject(methodObject) && !isWriteObject(methodObject) && !isEquals(methodObject) && !isHashCode(methodObject) && !isClone(methodObject) && !isCompareTo(methodObject) && !isToString(methodObject)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean validRemainingFieldsInSourceClass() {
		for(MyAttribute sourceAttribute : sourceClass.getAttributeList()) {
			if(!extractedEntities.contains(sourceAttribute)) {
				FieldObject fieldObject = sourceAttribute.getFieldObject();
				if(!fieldObject.isStatic()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isReadObject(MyMethod method) {
		return isReadObject(method.getMethodObject());
	}

	private boolean isReadObject(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("readObject") && parameterTypeList.size() == 1 && parameterTypeList.get(0).getClassType().equals("java.io.ObjectInputStream");
	}

	private boolean isWriteObject(MyMethod method) {
		return isWriteObject(method.getMethodObject());
	}

	private boolean isWriteObject(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("writeObject") && parameterTypeList.size() == 1 && parameterTypeList.get(0).getClassType().equals("java.io.ObjectOutputStream");
	}

	private boolean isEquals(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("equals") && methodObject.getReturnType().getClassType().equals("boolean") &&
				parameterTypeList.size() == 1 && parameterTypeList.get(0).getClassType().equals("java.lang.Object");
	}

	private boolean isHashCode(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("hashCode") && methodObject.getReturnType().getClassType().equals("int") && parameterTypeList.size() == 0;
	}

	private boolean isToString(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("toString") && methodObject.getReturnType().getClassType().equals("java.lang.String") && parameterTypeList.size() == 0;
	}

	private boolean isClone(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("clone") && methodObject.getReturnType().getClassType().equals("java.lang.Object") && parameterTypeList.size() == 0;
	}

	private boolean isCompareTo(MethodObject methodObject) {
		List<TypeObject> parameterTypeList = methodObject.getParameterTypeList();
		return methodObject.getName().equals("compareTo") && methodObject.getReturnType().getClassType().equals("int") && parameterTypeList.size() == 1;
	}

	private boolean containsFieldAccessOfEnclosingClass(MyMethod method) {
        return method.getMethodObject().containsFieldAccessOfEnclosingClass();
	}

	private boolean overridesMethod(MyMethod method) {
        //System.out.println(this.toString() + "\toverrides method of superclass");
        return method.getMethodObject().overridesMethod();
	}

	private boolean containsSuperMethodInvocation(MyMethod method) {
        //System.out.println(this.toString() + "\tcontains super method invocation");
        return method.getMethodObject().containsSuperMethodInvocation();
	}

	private boolean isSynchronized(MyMethod method) {
        //System.out.println(this.toString() + "\tis synchronized");
        return method.getMethodObject().isSynchronized();
	}

	@Override
	public Set<String> getEntitySet() {
		return sourceClass.getEntitySet();
	}
	@Override
	public List<Position> getPositions() {
		ArrayList<Position> positions = new ArrayList<Position>();
		for(Entity entity : extractedEntities) {
			if(entity instanceof MyMethod method) {
                Position position = new Position(method.getMethodObject().getMethodDeclaration().getStartPosition(), method.getMethodObject().getMethodDeclaration().getLength());
				positions.add(position);
			} else if(entity instanceof MyAttribute) {
				MyAttribute attribute = (MyAttribute)entity;
				VariableDeclarationFragment fragment = attribute.getFieldObject().getVariableDeclarationFragment();
				FieldDeclaration fieldDeclaration = (FieldDeclaration)fragment.getParent();
				Position position = null;
				if(fieldDeclaration.fragments().size() > 1) {
					position = new Position(fragment.getStartPosition(), fragment.getLength());
				}
				else {
					position = new Position(fieldDeclaration.getStartPosition(), fieldDeclaration.getLength());
				}
				positions.add(position);
			}
		}
		return positions;
	}

	@Override
	public String getSource() {
		return sourceClass.getName();
	}

	@Override
	public TypeDeclaration getSourceClassTypeDeclaration() {
		return (TypeDeclaration)sourceClass.getClassObject().getAbstractTypeDeclaration();
	}

	@Override
	public String getSourceEntity() {
		return sourceClass.toString();
	}

	@Override
	public String getTarget() {
		return null;
	}

	@Override
	public TypeDeclaration getTargetClassTypeDeclaration() {
		return null;
	}

	public String toString() {
        return sourceClass.toString() + "\t" + extractedEntities.toString();
    }

	public String getAnnotationText() {
		return visualizationData.toString();
	}

	public GodClassVisualizationData getGodClassVisualizationData() {
		return visualizationData;
	}

	@Override
	public IFile getSourceIFile() {
		return sourceClass.getClassObject().getIFile();
	}

	@Override
	public IFile getTargetIFile() {
		return null;
	}

	public Integer getUserRate() {
		return userRate;
	}

	public void setUserRate(Integer userRate) {
		this.userRate = userRate;
	}

	public int compareTo(ExtractClassCandidateRefactoring other) {
		int thisSourceClassDependencies = this.getDistinctSourceDependencies();
		int otherSourceClassDependencies = other.getDistinctSourceDependencies();
		if(thisSourceClassDependencies != otherSourceClassDependencies) {
			return Integer.compare(thisSourceClassDependencies, otherSourceClassDependencies);
		}
		else {
			int thisTargetClassDependencies = this.getDistinctTargetDependencies();
			int otherTargetClassDependencies = other.getDistinctTargetDependencies();
			if(thisTargetClassDependencies != otherTargetClassDependencies) {
				return -Integer.compare(thisTargetClassDependencies, otherTargetClassDependencies);
			}
			else {
				return this.sourceClass.getName().compareTo(other.sourceClass.getName());
			}
		}
	}

	public void findTopics() {
		List<String> codeElements = new ArrayList<String>();
		for (Entity entity : this.extractedEntities) {
			if (entity instanceof MyAttribute attribute) {
                codeElements.add(attribute.getName());
			}
			else if (entity instanceof MyMethod method) {
                codeElements.add(method.getMethodName());
			}
		}
		this.topics = TopicFinder.findTopics(codeElements);
	}

	public List<String> getTopics() {
		return topics;
	}

	public int getDistinctSourceDependencies() {
		return visualizationData.getDistinctSourceDependencies();
	}

	public int getDistinctTargetDependencies() {
		return visualizationData.getDistinctTargetDependencies();
	}
}