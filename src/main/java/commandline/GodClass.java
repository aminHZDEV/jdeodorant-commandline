package commandline;

import distance.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GodClass {
    private final ExtractClassCandidateGroup[] candidateRefactoringTable;

    // Constructor that accepts initialized candidates
    public GodClass(ExtractClassCandidateGroup[] candidateRefactoringTable) {
        this.candidateRefactoringTable = candidateRefactoringTable;
    }

    public void saveResults(String filePath) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(filePath))) {
            for (ExtractClassCandidateGroup group : candidateRefactoringTable) {
                List<String> outputLines = new ArrayList<>();
                String className = group.getSource();
                for (ExtractClassCandidateRefactoring candidate : group.getCandidates()) {
                    List<String> signatures = new ArrayList<>();
                    for (Entity entity : candidate.getExtractedEntities()) {
                        if (entity instanceof MyMethod method) {
                            String signature = String.format("%s::%s(%s):%s",
                                    method.getClassOrigin(), // Class that the method belongs to
                                    method.getMethodName(),
                                    String.join(", ", method.getParameterList()),
                                    method.getReturnType());
                            signatures.add(signature);
                        } else if (entity instanceof MyAttribute attribute) {
                            String signature = String.format("%s::%s %s",
                                    attribute.getClassOrigin(),
                                    attribute.getClassType(),
                                    attribute.getName());
                            signatures.add(signature);
                        }
                    }
                    String outputLine = className + "\t[" + String.join(", ", signatures) + "]";
                    outputLines.add(outputLine);
                }

                // Write all lines for this group in the desired format
                for (String line : outputLines) {
                    out.write(line + "\n");
                }
            }
            System.out.println("Results saved to " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
