import ast.SystemObject; // Assuming you have this imported in your project
import commandline.GodClass;
import distance.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JdApplication {

    public static void main(String[] args) {
        System.out.println("Starting application...");

        if (args.length < 3) {  // Changed to expect 3 arguments
            System.out.println("Please provide project name, target workspace path, and file path to save results.");
            return;
        }

        String projectName = args[0]; // Name of the project to process
        String workspacePath = args[1]; // Path to the workspace
        String resultsFilePath = args[2]; // Path to save results

        try {
            startWorkspaceProcessing(workspacePath, projectName, resultsFilePath);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    // Modified method to accept project name
    private static void startWorkspaceProcessing(String workspacePath, String projectName, String resultsFilePath) throws JavaModelException {
        List<ExtractClassCandidateGroup> groups = new ArrayList<>();
        processProject(workspacePath, projectName, groups);
        new GodClass(groups.toArray(new ExtractClassCandidateGroup[0])).saveResults(resultsFilePath);
    }

    // Adjusted method to process only the specified project
    private static void processProject(String workspacePath, String projectName, List<ExtractClassCandidateGroup> groups) throws JavaModelException {
        File workspace = new File(workspacePath);
        System.out.println("Workspace path: " + workspace.getAbsolutePath());

        // Check if workspace exists and is a directory
        if (!workspace.exists() || !workspace.isDirectory()) {
            System.err.println("Workspace does not exist or is not a directory: " + workspacePath);
            return;
        }

        // Get the project folder based on the project name
        File projectFolder = new File(workspace, projectName);
        if (!projectFolder.exists() || !projectFolder.isDirectory()) {
            System.err.println("Project '" + projectName + "' does not exist in the workspace.");
            return;
        }

        // Process the specified project
        System.out.println("Processing project: " + projectFolder.getName());
        analyzeProjectFiles(projectFolder, groups);
    }

    private static void analyzeProjectFiles(File projectFolder, List<ExtractClassCandidateGroup> groups) {
        System.out.println("Analyzing project folder: " + projectFolder.getAbsolutePath());

        // Use a list to gather all .java files
        List<File> javaFiles = new ArrayList<>();
        findJavaFiles(projectFolder, javaFiles);

        System.out.println("Found " + javaFiles.size() + " .java files in directory: " + projectFolder.getAbsolutePath());

        if (javaFiles.isEmpty()) {
            System.err.println("No .java files found in the project folder.");
            return;
        }

        for (File file : javaFiles) {
            try {
                System.out.println("Processing file: " + file.getAbsolutePath());
                String source = new String(Files.readAllBytes(file.toPath()));
                analyzeCompilationUnit(source, groups, file.getName().replace(".java", "")); // Pass file name excluding .java
            } catch (IOException e) {
                System.err.println("Failed to read file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    private static void findJavaFiles(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively search in subdirectories
                    findJavaFiles(file, javaFiles);
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
    }

    private static void analyzeCompilationUnit(String sourceCode, List<ExtractClassCandidateGroup> groups, String className) {
        try {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(sourceCode.toCharArray());
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            cu.accept(new ASTVisitor() {
                @Override
                public void endVisit(TypeDeclaration node) {
                    MyClass sourceClass = new MyClass(className);
                    List<Entity> extractedEntities = new ArrayList<>();

                    for (MethodDeclaration method : node.getMethods()) {
                        if (method != null) { // Check if method is not null
                            String methodName = method.getName().getFullyQualifiedName();
                            String returnType = method.getReturnType2() != null ? method.getReturnType2().toString() : "void";
                            List<String> parameterList = new ArrayList<>();
                            for (Object param : method.parameters()) {
                                // Ensure each parameter is valid
                                if (param instanceof SingleVariableDeclaration variable) {
                                    String paramType = variable.getType().toString();
                                    parameterList.add(paramType);
                                }
                            }
                            MyMethod myMethod = new MyMethod(className, methodName, returnType, parameterList);
                            extractedEntities.add(myMethod);
                        }
                    }

                    for (FieldDeclaration field : node.getFields()) {
                        if (field != null) { // Check if field is not null
                            String fieldType = field.getType().toString();
                            for (Object fragment : field.fragments()) {
                                if (fragment instanceof VariableDeclarationFragment varFragment) {
                                    String fieldName = varFragment.getName().getFullyQualifiedName();
                                    MyAttribute myAttribute = new MyAttribute(className, fieldType, fieldName);
                                    extractedEntities.add(myAttribute);
                                }
                            }
                        }
                    }

                    ExtractClassCandidateGroup group = getExtractClassCandidateGroup(sourceClass, extractedEntities, className);
                    groups.add(group);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ExtractClassCandidateGroup getExtractClassCandidateGroup(MyClass sourceClass, List<Entity> extractedEntities, String className) {
        SystemObject systemObject = new SystemObject();
        MySystem system = new MySystem(systemObject, false);
        ExtractClassCandidateRefactoring candidate = new ExtractClassCandidateRefactoring(system, sourceClass, new ArrayList<>(extractedEntities));
        ExtractClassCandidateGroup group = new ExtractClassCandidateGroup(className);
        group.addCandidate(candidate);
        return group;
    }
}