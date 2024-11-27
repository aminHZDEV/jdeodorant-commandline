package ast.decomposition.cfg.mapping;

import ast.decomposition.cfg.GraphEdge;
import ast.decomposition.cfg.PDG;
import ast.decomposition.cfg.PDGBlockNode;
import ast.decomposition.cfg.PDGControlDependence;
import ast.decomposition.cfg.PDGControlPredicateNode;
import ast.decomposition.cfg.PDGDependence;
import ast.decomposition.cfg.PDGMethodEntryNode;
import ast.decomposition.cfg.PDGNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;

public class PDGRegionSubTreeMapper extends DivideAndConquerMatcher {
	private List<ASTNode> cloneFragmentASTNodes1;
	private List<ASTNode> cloneFragmentASTNodes2;
	
	public PDGRegionSubTreeMapper(PDG pdg1, PDG pdg2,
								  CompilationUnit iCompilationUnit1, CompilationUnit iCompilationUnit2,
								  ControlDependenceTreeNode controlDependenceSubTreePDG1,
								  ControlDependenceTreeNode controlDependenceSubTreePDG2,
								  List<ASTNode> ASTNodes1,
								  List<ASTNode> ASTNodes2,
								  boolean fullTreeMatch, IProgressMonitor monitor) {
		super(pdg1, pdg2, iCompilationUnit1, iCompilationUnit2, controlDependenceSubTreePDG1, controlDependenceSubTreePDG2, fullTreeMatch, monitor);
		this.cloneFragmentASTNodes1 = ASTNodes1;
		this.cloneFragmentASTNodes2 = ASTNodes2;
		//creates CloneStructureRoot
		matchBasedOnControlDependenceTreeStructure();
		this.preconditionExaminer = new PreconditionExaminer(pdg1, pdg2, iCompilationUnit1, iCompilationUnit2,
				getCloneStructureRoot(), getMaximumStateWithMinimumDifferences(), getAllNodesInSubTreePDG1(), getAllNodesInSubTreePDG2());
	}

	protected Set<PDGNode> getNodesInRegion1(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, ControlDependenceTreeNode controlDependenceTreeRoot) {
		return getNodesInRegion(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel, controlDependenceTreeRoot, cloneFragmentASTNodes1);
	}

	protected Set<PDGNode> getNodesInRegion2(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, ControlDependenceTreeNode controlDependenceTreeRoot) {
		return getNodesInRegion(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel, controlDependenceTreeRoot, cloneFragmentASTNodes2);
	}

	protected Set<PDGNode> getElseNodesOfSymmetricalIfStatement1(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel) {
		return getElseNodesOfSymmetricalIfStatement(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel, cloneFragmentASTNodes1);
	}

	protected Set<PDGNode> getElseNodesOfSymmetricalIfStatement2(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel) {
		return getElseNodesOfSymmetricalIfStatement(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel, cloneFragmentASTNodes2);
	}

	protected List<ControlDependenceTreeNode> getIfParentChildren1(ControlDependenceTreeNode cdtNode) {
		return getIfParentChildren(cdtNode, cloneFragmentASTNodes1);
	}

	protected List<ControlDependenceTreeNode> getIfParentChildren2(ControlDependenceTreeNode cdtNode) {
		return getIfParentChildren(cdtNode, cloneFragmentASTNodes2);
	}

	private Set<PDGNode> getNodesInRegion(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, ControlDependenceTreeNode controlDependenceTreeRoot, List<ASTNode> cloneFragmentNodes) {
		Set<PDGNode> nodesInRegion = new TreeSet<PDGNode>();
		if(!(controlPredicate instanceof PDGMethodEntryNode) &&
				!controlPredicate.equals(controlDependenceTreeRoot.getNode()) && cloneFragmentContainsPDGNode(cloneFragmentNodes,controlPredicate))
			nodesInRegion.add(controlPredicate);
		if(controlPredicate instanceof PDGBlockNode) {
			Set<PDGNode> nestedNodesWithinTryNode = pdg.getNestedNodesWithinBlockNode((PDGBlockNode)controlPredicate);
			for(PDGNode nestedNode : nestedNodesWithinTryNode) {
				if(!controlPredicateNodesInNextLevel.contains(nestedNode) && !controlPredicateNodesInCurrentLevel.contains(nestedNode)) {
					if(!(nestedNode instanceof PDGControlPredicateNode) && !(nestedNode instanceof PDGBlockNode) && cloneFragmentContainsPDGNode(cloneFragmentNodes,nestedNode))
						nodesInRegion.add(nestedNode);
				}
			}
		}
		else {
			Iterator<GraphEdge> edgeIterator = controlPredicate.getOutgoingDependenceIterator();
			while(edgeIterator.hasNext()) {
				PDGDependence dependence = (PDGDependence)edgeIterator.next();
				if(dependence instanceof PDGControlDependence) {
					PDGNode pdgNode = (PDGNode)dependence.getDst();
					PDGBlockNode tryNode = pdg.isDirectlyNestedWithinBlockNode(pdgNode);
					if(!controlPredicateNodesInNextLevel.contains(pdgNode) && !controlPredicateNodesInCurrentLevel.contains(pdgNode) && tryNode == null) {
						if(!(pdgNode instanceof PDGControlPredicateNode) && !(pdgNode instanceof PDGBlockNode) && cloneFragmentContainsPDGNode(cloneFragmentNodes,pdgNode))
							nodesInRegion.add(pdgNode);
					}
				}
			}
		}
		return nodesInRegion;
	}
	
	private Set<PDGNode> getElseNodesOfSymmetricalIfStatement(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, List<ASTNode> cloneFragmentNodes) {
		Set<PDGNode> nodesInRegion = new TreeSet<PDGNode>();
		Iterator<GraphEdge> edgeIterator = controlPredicate.getOutgoingDependenceIterator();
		while(edgeIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)edgeIterator.next();
			if(dependence instanceof PDGControlDependence) {
				PDGControlDependence pdgControlDependence = (PDGControlDependence)dependence;
				if(pdgControlDependence.isFalseControlDependence()) {
					PDGNode pdgNode = (PDGNode)dependence.getDst();
					PDGBlockNode tryNode = pdg.isDirectlyNestedWithinBlockNode(pdgNode);
					if(!controlPredicateNodesInNextLevel.contains(pdgNode) && !controlPredicateNodesInCurrentLevel.contains(pdgNode) && tryNode == null) {
						if(!(pdgNode instanceof PDGControlPredicateNode)  && cloneFragmentContainsPDGNode(cloneFragmentNodes,pdgNode))
							nodesInRegion.add(pdgNode);
					}
				}
			}
		}
		return nodesInRegion;
	}

	private List<ControlDependenceTreeNode> getIfParentChildren(ControlDependenceTreeNode cdtNode, List<ASTNode> cloneFragmentNodes) {
		List<ControlDependenceTreeNode> children = new ArrayList<ControlDependenceTreeNode>();
		if(cdtNode != null && cdtNode.isElseNode()) {
			ControlDependenceTreeNode ifParent = cdtNode.getIfParent();
			if(ifParent != null && cloneFragmentContainsPDGNode(cloneFragmentNodes, ifParent.getNode())) {
				children.addAll(ifParent.getChildren());
			}
		}
		return children;
	}

	private boolean cloneFragmentContainsPDGNode(List<ASTNode> cloneFragmentNodes, PDGNode pdgNode) {
		Statement pdgStatement = pdgNode.getASTStatement();
		int start = pdgStatement.getStartPosition();
		int cloneFragmentStart = cloneFragmentNodes.get(0).getStartPosition();
		int cloneFragmentLastNodeStart = cloneFragmentNodes.get(cloneFragmentNodes.size()-1).getStartPosition();
		int cloneFragmentEnd = cloneFragmentLastNodeStart + cloneFragmentNodes.get(cloneFragmentNodes.size()-1).getLength();
		if (start >= cloneFragmentStart && start <= cloneFragmentEnd)
			return true;
		else 
			return false;
	}
}
