package jdeodorant.eclipse.commandline.cloneinfowriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdeodorant.eclipse.commandline.coverage.TestReportResults.TestReportDifference;
import ast.decomposition.cfg.mapping.DivideAndConquerMatcher;

public class PDGSubTreeMapperInfo {
	private final DivideAndConquerMatcher mapper;
	private long timeElapsedToCalculate;
	
	private long wallNanoTimeElapsedForMapping;
	private List<TestReportDifference> testResultsDifferences = new ArrayList<>();
	private final Set<String> filesHavingCompileErrors = new HashSet<>();
	private boolean refactoringWasOk;
	
	public long getWallNanoTimeElapsedForMapping() {
		return wallNanoTimeElapsedForMapping;
	}

	public void setWallNanoTimeElapsedForMapping(long wallNanoTime) {
		this.wallNanoTimeElapsedForMapping = wallNanoTime;
	}

	public PDGSubTreeMapperInfo(DivideAndConquerMatcher mapper) {
		this.mapper = mapper;
	}
	
	public DivideAndConquerMatcher getMapper() {
		return this.mapper;
	}

	/**
	 * Get time elapsed for only mapping phase (excluding the bottom-up subtree matching) 
	 * @return
	 */
	public long getTimeElapsedToMap() {
		return timeElapsedToCalculate;
	}

	/**
	 * Set time elapsed for only mapping phase (excluding the bottom-up subtree matching) 
	 * @return
	 */
	public void setTimeElapsedForMapping(long timeElapsedToCalculate) {
		this.timeElapsedToCalculate = timeElapsedToCalculate;
	}
	
	/**
	 * Is this mapper refactorable?
	 * @deprecated
	 */
	public boolean isRefactorable() {
		return this.mapper != null && this.mapper.getPreconditionViolations().isEmpty() &&
                !this.mapper.getRemovableNodesG1().isEmpty() && !this.mapper.getRemovableNodesG2().isEmpty();
	}
	

	public void addFileHavingCompileError(String path) {
		this.filesHavingCompileErrors.add(path);
	}
	
	public Iterable<String> getFilesHavingCompileError() {
		return this.filesHavingCompileErrors ;		
	}

	public void setTestDifferences(List<TestReportDifference> compareTestResults) {
		this.testResultsDifferences = compareTestResults;
	}
	
	public List<TestReportDifference> getTestDifferences() {
		return this.testResultsDifferences;
	}

	public void setRefactoringWasOK(boolean refactoringWasOk) {
		this.refactoringWasOk = refactoringWasOk;
	}
	
	public boolean getRefactoringWasOK() {
		return refactoringWasOk;
	}
	
	public boolean getHasCompileErrorsAfterRefactoring() {
		return !filesHavingCompileErrors.isEmpty();
	}
	
	public boolean testsFailedAfterRefactoring() {
		return !testResultsDifferences.isEmpty();
	}
}
