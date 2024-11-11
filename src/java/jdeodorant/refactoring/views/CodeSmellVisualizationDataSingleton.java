package java.jdeodorant.refactoring.views;

import java.ast.visualization.VisualizationData;
import java.distance.CandidateRefactoring;

public class CodeSmellVisualizationDataSingleton {
	private static VisualizationData data;
	private static CandidateRefactoring[] candidates;
	
	
	public static CandidateRefactoring[] getCandidates() {
		return candidates;
	}

	public static void setCandidates(CandidateRefactoring[] candidates) {
		CodeSmellVisualizationDataSingleton.candidates = candidates;
	}

	public static VisualizationData getData() {
		return data;
	}

	public static void setData(VisualizationData data) {
		CodeSmellVisualizationDataSingleton.data = data;
	}
}
