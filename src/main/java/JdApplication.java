import ast.ASTReader;
import ast.ClassObject;
import ast.CompilationErrorDetectedException;
import ast.SystemObject;
import commandline.GodClass;
import distance.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;

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
        String projectName = args[0];
        String workspacePath = args[1];
        String resultsFilePath = args[2];
        try {
            startWorkspaceProcessing(workspacePath, projectName, resultsFilePath);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void startWorkspaceProcessing(String workspacePath, String projectName, String resultsFilePath) throws JavaModelException {
        List<ExtractClassCandidateGroup> groups = new ArrayList<>();
        SystemObject systemObject = processProject(workspacePath, projectName, groups);
        if (systemObject != null) {
            MySystem mySystem = new MySystem(systemObject, true);
            DistanceMatrix distanceMatrix = new DistanceMatrix(mySystem);
            Set<String> classNamesToBeExamined = getClassNames(groups);
            List<ExtractClassCandidateRefactoring> extractClassCandidates = distanceMatrix.getExtractClassCandidateRefactorings(classNamesToBeExamined, null);
            if (!extractClassCandidates.isEmpty()) {
                ExtractClassCandidateGroup candidateGroup = new ExtractClassCandidateGroup(projectName);
                for (ExtractClassCandidateRefactoring candidate : extractClassCandidates)
                    candidateGroup.addCandidate(candidate);
                candidateGroup.groupConcepts();
                GodClass godClass = new GodClass(new ExtractClassCandidateGroup[]{candidateGroup});
                godClass.saveResults(resultsFilePath);
            } else
                System.out.println("No extract class candidates found.");
        }
    }

    private static SystemObject processProject(String workspacePath, String projectName, List<ExtractClassCandidateGroup> groups) throws JavaModelException {
        File workspace = new File(workspacePath);
        System.out.println("Workspace path: " + workspace.getAbsolutePath());
        if (!workspace.exists() || !workspace.isDirectory()) {
            System.err.println("Workspace does not exist or is not a directory: " + workspacePath);
            return null;
        }
        File projectFolder = new File(workspace, projectName);
        if (!projectFolder.exists() || !projectFolder.isDirectory()) {
            System.err.println("Project '" + projectName + "' does not exist in the workspace.");
            return null;
        }
        System.out.println("Processing project: " + projectFolder.getName());
        return analyzeProjectFiles(projectFolder, groups);
    }

    private static SystemObject analyzeProjectFiles(File projectFolder, List<ExtractClassCandidateGroup> groups) {
        System.out.println("Analyzing project folder: " + projectFolder.getAbsolutePath());
        List<File> javaFiles = new ArrayList<>();
        findJavaFiles(projectFolder, javaFiles);
        if (javaFiles.isEmpty()) {
            System.err.println("No .java files found in the project folder.");
            return null;
        }
        StringBuilder combinedSource = new StringBuilder();
        for (File file : javaFiles) {
            try {
                System.out.println("Processing file: " + file.getAbsolutePath());
                String source = new String(Files.readAllBytes(file.toPath()));
                combinedSource.append(source).append("\n");
            } catch (IOException e) {
                System.err.println("Failed to read file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
        return analyzeCompilationUnit(combinedSource.toString(), groups, projectFolder.getName());
    }

    private static SystemObject analyzeCompilationUnit(String sourceCode, List<ExtractClassCandidateGroup> groups, String projectName) {
        try {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(sourceCode.toCharArray());
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            cu.accept(new ASTVisitor() {
                @Override
                public void endVisit(TypeDeclaration node) {
                    MyClass sourceClass = new MyClass(node.getName().getFullyQualifiedName());
                    List<Entity> extractedEntities = new ArrayList<>(extractEntities(node, sourceClass));
                    ExtractClassCandidateGroup group = getExtractClassCandidateGroup(sourceClass, extractedEntities, projectName);
                    groups.add(group);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ASTReader.getSystemObject();
    }

    private static List<Entity> extractEntities(TypeDeclaration node, MyClass sourceClass) {
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
        return extractedEntities;
    }

    private static Set<String> getClassNames(List<ExtractClassCandidateGroup> groups) {
        Set<String> classNames = new LinkedHashSet<>();
        for (ExtractClassCandidateGroup group : groups)
            classNames.add(group.getSource());
        return classNames;
    }

    private static void findJavaFiles(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    findJavaFiles(file, javaFiles);
                else if (file.getName().endsWith(".java"))
                    javaFiles.add(file);
            }
        }
    }

    private static ExtractClassCandidateGroup getExtractClassCandidateGroup(MyClass sourceClass,
                                                                            List<Entity> extractedEntities,
                                                                            String projectName) {
        SystemObject systemObject = new SystemObject();
        MySystem system = new MySystem(systemObject,
                false);
        ExtractClassCandidateRefactoring candidate = new ExtractClassCandidateRefactoring(
                system,
                sourceClass,
                new ArrayList<>(extractedEntities)
        );
        ExtractClassCandidateGroup group = new ExtractClassCandidateGroup(
                projectName
        );
        group.addCandidate(
                candidate
        );
        return group;
    }
}