import commandline.ASTReader;
import ast.ClassObject;
import ast.CompilationErrorDetectedException;
import ast.SystemObject; // Assuming you have this imported in your project
import commandline.GodClass;
import distance.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JdApplication {

    public static void main(String[] args) {
        System.out.println("Starting application...");

        if (args.length < 3) {
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

    private static void startWorkspaceProcessing(String workspacePath, String projectName, String resultsFilePath) throws JavaModelException {
        List<ExtractClassCandidateGroup> groups = new ArrayList<>();
        processProject(workspacePath, projectName, groups);
        new GodClass(groups.toArray(new ExtractClassCandidateGroup[0])).saveResults(resultsFilePath);
    }

    private static void processProject(String workspacePath, String projectName, List<ExtractClassCandidateGroup> groups) throws JavaModelException {
        getTable(workspacePath+projectName, groups);
//        File workspace = new File(workspacePath);
//        System.out.println("Workspace path: " + workspace.getAbsolutePath());
//
//        if (!workspace.exists() || !workspace.isDirectory()) {
//            System.err.println("Workspace does not exist or is not a directory: " + workspacePath);
//            return;
//        }
//
//        File projectFolder = new File(workspace, projectName);
//        if (!projectFolder.exists() || !projectFolder.isDirectory()) {
//            System.err.println("Project '" + projectName + "' does not exist in the workspace.");
//            return;
//        }
//
//        System.out.println("Processing project: " + projectFolder.getName());
//        analyzeProjectFiles(projectFolder, groups);
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

        // Read all Java files and combine their contents
        StringBuilder combinedSource = new StringBuilder();
        for (File file : javaFiles) {
            try {
                System.out.println("Processing file: " + file.getAbsolutePath());
                String source = new String(Files.readAllBytes(file.toPath()));
                combinedSource.append(source).append("\n"); // Combine source with line breaks
            } catch (IOException e) {
                System.err.println("Failed to read file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }

        // Analyze the combined source as a single compilation unit
        analyzeCompilationUnit(combinedSource.toString(), groups, projectFolder.getName());
    }

    private static void findJavaFiles(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findJavaFiles(file, javaFiles);
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
    }

    private static void analyzeCompilationUnit(String sourceCode, List<ExtractClassCandidateGroup> groups, String projectName) {
        try {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(sourceCode.toCharArray());
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            cu.accept(new ASTVisitor() {
                @Override
                public void endVisit(TypeDeclaration node) {
                    MyClass sourceClass = new MyClass(node.getName().getFullyQualifiedName());
                    List<Entity> extractedEntities = new ArrayList<>();

                    for (MethodDeclaration method : node.getMethods()) {
                        if (method != null) {
                            String methodName = method.getName().getFullyQualifiedName();
                            String returnType = method.getReturnType2() != null ? method.getReturnType2().toString() : "void";
                            List<String> parameterList = new ArrayList<>();
                            for (Object param : method.parameters()) {
                                if (param instanceof SingleVariableDeclaration variable) {
                                    String paramType = variable.getType().toString();
                                    parameterList.add(paramType);
                                }
                            }
                            MyMethod myMethod = new MyMethod(sourceClass.getName(), methodName, returnType, parameterList);
                            extractedEntities.add(myMethod);
                        }
                    }

                    for (FieldDeclaration field : node.getFields()) {
                        if (field != null) {
                            String fieldType = field.getType().toString();
                            for (Object fragment : field.fragments()) {
                                if (fragment instanceof VariableDeclarationFragment varFragment) {
                                    String fieldName = varFragment.getName().getFullyQualifiedName();
                                    MyAttribute myAttribute = new MyAttribute(sourceClass.getName(), fieldType, fieldName);
                                    extractedEntities.add(myAttribute);
                                }
                            }
                        }
                    }

                    ExtractClassCandidateGroup group = getExtractClassCandidateGroup(sourceClass, extractedEntities, projectName);
                    groups.add(group);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ExtractClassCandidateGroup getExtractClassCandidateGroup(MyClass sourceClass, List<Entity> extractedEntities, String projectName) {
        SystemObject systemObject = new SystemObject();
        MySystem system = new MySystem(systemObject, false);
        final DistanceMatrix distanceMatrix = new DistanceMatrix(system);
        ExtractClassCandidateRefactoring candidate = new ExtractClassCandidateRefactoring(system, sourceClass, new ArrayList<>(extractedEntities));
        ExtractClassCandidateGroup group = new ExtractClassCandidateGroup(projectName);
        group.addCandidate(candidate);
        return group;
    }

    private static HashMap<String, ExtractClassCandidateGroup> getTable(String project_path, List<ExtractClassCandidateGroup> table) {
        HashMap<String, ExtractClassCandidateGroup> groupedBySourceClassMap = new HashMap<>();
        ASTReader astReader = null;
        try {
            astReader = new ASTReader(project_path);
        } catch (CompilationErrorDetectedException e) {
            System.err.println("Compilation errors were detected in the project. Fix the errors before using JDeodorant.");
            e.printStackTrace(); // Print stack trace for debugging
            return groupedBySourceClassMap; // Exit if errors are detected
        }
        SystemObject systemObject = astReader.getSystemObject();
        if (systemObject != null) {
            Set<ClassObject> classObjectsToBeExamined = new LinkedHashSet<>(systemObject.getClassObjects());
            final Set<String> classNamesToBeExamined = new LinkedHashSet<>();
            DistanceMatrix distanceMatrix = checkClassObjectsToBeExamined(systemObject, classObjectsToBeExamined, classNamesToBeExamined);
            final List<ExtractClassCandidateRefactoring> extractClassCandidateList = new ArrayList<>(distanceMatrix.getExtractClassCandidateRefactorings(classNamesToBeExamined, null));
            for (ExtractClassCandidateRefactoring candidate : extractClassCandidateList) {
                if (groupedBySourceClassMap.containsKey(candidate.getSourceEntity())) {
                    groupedBySourceClassMap.get(candidate.getSourceEntity()).addCandidate(candidate);
                } else {
                    ExtractClassCandidateGroup group = new ExtractClassCandidateGroup(candidate.getSourceEntity());
                    group.addCandidate(candidate);
                    groupedBySourceClassMap.put(candidate.getSourceEntity(), group);
                }
            }
            for (String sourceClass : groupedBySourceClassMap.keySet()) {
                groupedBySourceClassMap.get(sourceClass).groupConcepts();
            }

            table = List.of(new ExtractClassCandidateGroup[groupedBySourceClassMap.size()]);
            groupedBySourceClassMap.values().addAll(table);
        }
        return groupedBySourceClassMap;
    }

    public static DistanceMatrix checkClassObjectsToBeExamined(SystemObject systemObject, Set<ClassObject> classObjectsToBeExamined, Set<String> classNamesToBeExamined) {
        for (ClassObject classObject : classObjectsToBeExamined) {
            if (!classObject.isEnum() && !classObject.isInterface() && !classObject.isGeneratedByParserGenenator())
                classNamesToBeExamined.add(classObject.getName());
        }
        MySystem system = new MySystem(systemObject, true);
        return new DistanceMatrix(system);
    }
}