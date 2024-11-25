package commandline;

import distance.ExtractClassCandidateGroup;
import distance.ExtractClassCandidateRefactoring;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class GodClass {
    private final ExtractClassCandidateGroup[] candidateRefactoringTable;

    // Constructor that accepts initialized candidates
    public GodClass(ExtractClassCandidateGroup[] candidateRefactoringTable) {
        this.candidateRefactoringTable = candidateRefactoringTable;
    }

    public void saveResults(String filePath) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(filePath))) {
            for (ExtractClassCandidateGroup group : candidateRefactoringTable) {
                out.write("Class: " + group.getSource() + "\n");
                for (ExtractClassCandidateRefactoring candidate : group.getCandidates()) {
                    out.write("  Candidate: " + candidate.toString() + "\n");
                }
            }
            System.out.println("Results saved to " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
