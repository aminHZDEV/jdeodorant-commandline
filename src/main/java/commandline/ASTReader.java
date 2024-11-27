package commandline;


import ast.*;
import ast.decomposition.AbstractExpression;
import ast.decomposition.MethodBodyObject;
import ast.util.StatementExtractor;
//import org.eclipse.core.filebuffers.FileBuffers;
//import org.eclipse.core.filebuffers.ITextFileBuffer;
//import org.eclipse.core.filebuffers.ITextFileBufferManager;
//import org.eclipse.core.filebuffers.LocationKind;
//import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IDocElement;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class ASTReader {

    private static SystemObject systemObject;
    public static final int JLS = AST.getJLSLatest();

    public ASTReader(String projectPath) throws CompilationErrorDetectedException {
        systemObject = new SystemObject();
        buildProject();
        try {
            processJavaFiles(projectPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void buildProject() {
        // Here you would implement any necessary validation logic for the project
        // This might include checking if the projectPath is valid or if Java files exist
    }

    private void processJavaFiles(String directoryPath) {
        try {
            Files.walk(Paths.get(directoryPath))
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(javaFilePath -> {
                        try {
                            // Pass the full path to parseAST and ensure it reads correctly
                            List<ClassObject> classes = parseAST(javaFilePath.toFile());
                            systemObject.addClasses(classes);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<ClassObject> parseAST(File javaFile) throws IOException {
        // Read the content of the file into a String
        String source = new String(Files.readAllBytes(javaFile.toPath()));

        // Ensure that the ASTParser is instantiated correctly
        ASTParser parser = ASTParser.newParser(JLS);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setResolveBindings(true); // Enable binding resolution

        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        // Check if the parsed CompilationUnit is valid
        if (compilationUnit == null || compilationUnit.types().isEmpty()) {
            System.err.println("Failed to parse AST for file: " + javaFile.getAbsolutePath());
            return new ArrayList<>(); // Return an empty list if parsing failed
        }

        return parseAST(compilationUnit);
    }

    private List<ClassObject> parseAST(CompilationUnit compilationUnit) {
        List<Comment> comments = compilationUnit.getCommentList();
        List<ClassObject> classObjects = new ArrayList<>();
        List<AbstractTypeDeclaration> topLevelTypeDeclarations = compilationUnit.types();
        for (AbstractTypeDeclaration abstractTypeDeclaration : topLevelTypeDeclarations) {
            if (abstractTypeDeclaration instanceof TypeDeclaration) {
                ClassObject classObject = processTypeDeclaration(null, null, (TypeDeclaration) abstractTypeDeclaration, comments);
                classObjects.add(classObject);
            } else if (abstractTypeDeclaration instanceof EnumDeclaration) {
                ClassObject classObject = processEnumDeclaration((EnumDeclaration) abstractTypeDeclaration, comments);
                classObjects.add(classObject);
            }
        }
        return classObjects;
    }



    public static List<AbstractTypeDeclaration> getRecursivelyInnerTypes(AbstractTypeDeclaration typeDeclaration) {
        List<AbstractTypeDeclaration> innerTypeDeclarations = new ArrayList<AbstractTypeDeclaration>();
        StatementExtractor statementExtractor = new StatementExtractor();
        List<BodyDeclaration> bodyDeclarations = typeDeclaration.bodyDeclarations();
        for(BodyDeclaration bodyDeclaration : bodyDeclarations) {
            if(bodyDeclaration instanceof MethodDeclaration methodDeclaration) {
                checkMethodDeclareBody(innerTypeDeclarations, statementExtractor, methodDeclaration);
            }
            else if(bodyDeclaration instanceof TypeDeclaration type) {
                innerTypeDeclarations.add(type);
                innerTypeDeclarations.addAll(getRecursivelyInnerTypes(type));
            }
            else if(bodyDeclaration instanceof EnumDeclaration type) {
                innerTypeDeclarations.add(type);
                innerTypeDeclarations.addAll(getRecursivelyInnerTypes(type));
            }
        }
        return innerTypeDeclarations;
    }

    public static void checkMethodDeclareBody(List<AbstractTypeDeclaration> innerTypeDeclarations, StatementExtractor statementExtractor, MethodDeclaration methodDeclaration) {
        if(methodDeclaration.getBody() != null) {
            List<Statement> statements = statementExtractor.getTypeDeclarationStatements(methodDeclaration.getBody());
            for(Statement statement : statements) {
                TypeDeclarationStatement typeDeclarationStatement = (TypeDeclarationStatement)statement;
                AbstractTypeDeclaration declaration = typeDeclarationStatement.getDeclaration();
                if(declaration instanceof TypeDeclaration) {
                    innerTypeDeclarations.add((TypeDeclaration)declaration);
                }
            }
        }
    }

//    private List<ClassObject> parseAST(CompilationUnit iCompilationUnit) {
//        ASTInformationGenerator.set(iCompilationUnit);
//        File iFile = (File)iCompilationUnit.get();
//        ASTParser parser = ASTParser.newParser(JLS);
//        parser.setKind(ASTParser.K_COMPILATION_UNIT);
//        parser.setSource(iCompilationUnit);
//        parser.setResolveBindings(true); // we need bindings later on
//        CompilationUnit compilationUnit = (CompilationUnit)parser.createAST(null);
//
//        return parseAST(compilationUnit, iFile);
//    }

//    private List<ClassObject> parseAST(CompilationUnit compilationUnit, IFile iFile) {
//        ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
//        IPath path = compilationUnit.getJavaElement().getPath();
//        try {
//            bufferManager.connect(path, LocationKind.IFILE, null);
//        } catch (CoreException e) {
//            e.printStackTrace();
//        }
//        ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
//        IDocument document = textFileBuffer.getDocument();
//        List<Comment> comments = compilationUnit.getCommentList();
//        List<ClassObject> classObjects = new ArrayList<ClassObject>();
//        List<AbstractTypeDeclaration> topLevelTypeDeclarations = compilationUnit.types();
//        for(AbstractTypeDeclaration abstractTypeDeclaration : topLevelTypeDeclarations) {
//            if(abstractTypeDeclaration instanceof TypeDeclaration topLevelTypeDeclaration) {
//                List<AbstractTypeDeclaration> typeDeclarations = new ArrayList<AbstractTypeDeclaration>();
//                typeDeclarations.add(topLevelTypeDeclaration);
//                typeDeclarations.addAll(getRecursivelyInnerTypes(topLevelTypeDeclaration));
//                for(AbstractTypeDeclaration typeDeclaration : typeDeclarations) {
//                    if(typeDeclaration instanceof TypeDeclaration) {
//                        final ClassObject classObject = processTypeDeclaration(iFile, document, (TypeDeclaration)typeDeclaration, comments);
//                        classObjects.add(classObject);
//                    }
//                    else if(typeDeclaration instanceof EnumDeclaration) {
//                        final ClassObject classObject = processEnumDeclaration(iFile, document, (EnumDeclaration)typeDeclaration, comments);
//                        classObjects.add(classObject);
//                    }
//                }
//            }
//            else if(abstractTypeDeclaration instanceof EnumDeclaration enumDeclaration) {
//                final ClassObject classObject = processEnumDeclaration(iFile, document, enumDeclaration, comments);
//                classObjects.add(classObject);
//            }
//        }
//        return classObjects;
//    }

    private List<CommentObject> processComments(File iFile, Document iDocument,
                                                AbstractTypeDeclaration typeDeclaration, List<Comment> comments) {
        List<CommentObject> commentList = new ArrayList<>();
        int typeDeclarationStartPosition = typeDeclaration.getStartPosition();
        int typeDeclarationEndPosition = typeDeclarationStartPosition + typeDeclaration.getLength();
        for (Comment comment : comments) {
            int commentStartPosition = comment.getStartPosition();
            int commentEndPosition = commentStartPosition + comment.getLength();

            int commentStartLine = 0;
            int commentEndLine = 0;
            String text = null;
            try {
                commentStartLine = iDocument.getLineOfOffset(commentStartPosition);
                commentEndLine = iDocument.getLineOfOffset(commentEndPosition);
                text = iDocument.get(commentStartPosition, comment.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

            CommentType type = null;
            if (comment.isLineComment()) {
                type = CommentType.LINE;
            } else if (comment.isBlockComment()) {
                type = CommentType.BLOCK;
            } else if (comment.isDocComment()) {
                type = CommentType.JAVADOC;
            }

            CommentObject commentObject = new CommentObject(text, type, commentStartLine, commentEndLine);
            commentObject.setComment(comment);

            String fileExtension = getFileExtension(iFile);
            if (typeDeclarationStartPosition <= commentStartPosition && typeDeclarationEndPosition >= commentEndPosition) {
                commentList.add(commentObject);
            } else if (iFile.getName().equals(typeDeclaration.getName().getIdentifier() + fileExtension)) {
                commentList.add(commentObject);
            }
        }
        return commentList;
    }

    private ClassObject processTypeDeclaration(File iFile, Document document, TypeDeclaration typeDeclaration, List<Comment> comments) {
        final ClassObject classObject = new ClassObject();
        classObject.setIFile(iFile);
        classObject.addComments(processComments(iFile, document, typeDeclaration, comments));
        ITypeBinding typeDeclarationBinding = typeDeclaration.resolveBinding();
        if(typeDeclarationBinding.isLocal()) {
            ITypeBinding declaringClass = typeDeclarationBinding.getDeclaringClass();
            String className = declaringClass.getQualifiedName() + "." + typeDeclarationBinding.getName();
            classObject.setName(className);
        }
        else {
            classObject.setName(typeDeclarationBinding.getQualifiedName());
        }
        classObject.setAbstractTypeDeclaration(typeDeclaration);

        if(typeDeclaration.isInterface()) {
            classObject.setInterface(true);
        }

        int modifiers = typeDeclaration.getModifiers();
        if((modifiers & Modifier.ABSTRACT) != 0)
            classObject.setAbstract(true);

        if((modifiers & Modifier.PUBLIC) != 0)
            classObject.setAccess(Access.PUBLIC);
        else if((modifiers & Modifier.PROTECTED) != 0)
            classObject.setAccess(Access.PROTECTED);
        else if((modifiers & Modifier.PRIVATE) != 0)
            classObject.setAccess(Access.PRIVATE);
        else
            classObject.setAccess(Access.NONE);

        if((modifiers & Modifier.STATIC) != 0)
            classObject.setStatic(true);

        Type superclassType = typeDeclaration.getSuperclassType();
        if(superclassType != null) {
            ITypeBinding binding = superclassType.resolveBinding();
            String qualifiedName = binding.getQualifiedName();
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            classObject.setSuperclass(typeObject);
        }

        List<Type> superInterfaceTypes = typeDeclaration.superInterfaceTypes();
        for(Type interfaceType : superInterfaceTypes) {
            ITypeBinding binding = interfaceType.resolveBinding();
            String qualifiedName = binding.getQualifiedName();
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            classObject.addInterface(typeObject);
        }

        FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();
        for(FieldDeclaration fieldDeclaration : fieldDeclarations) {
            processFieldDeclaration(classObject, fieldDeclaration);
        }

        MethodDeclaration[] methodDeclarations = typeDeclaration.getMethods();
        for(MethodDeclaration methodDeclaration : methodDeclarations) {
            processMethodDeclaration(classObject, methodDeclaration);
        }
        return classObject;
    }

    private ClassObject processEnumDeclaration(EnumDeclaration enumDeclaration, List<Comment> comments) {
        final ClassObject classObject = new ClassObject();
        classObject.setEnum(true);
        classObject.setIFile(null);
        classObject.addComments(processComments(null, null, enumDeclaration, comments));
        classObject.setName(enumDeclaration.resolveBinding().getQualifiedName());
        classObject.setAbstractTypeDeclaration(enumDeclaration);

        int modifiers = enumDeclaration.getModifiers();
        if((modifiers & Modifier.ABSTRACT) != 0)
            classObject.setAbstract(true);

        if((modifiers & Modifier.PUBLIC) != 0)
            classObject.setAccess(Access.PUBLIC);
        else if((modifiers & Modifier.PROTECTED) != 0)
            classObject.setAccess(Access.PROTECTED);
        else if((modifiers & Modifier.PRIVATE) != 0)
            classObject.setAccess(Access.PRIVATE);
        else
            classObject.setAccess(Access.NONE);

        if((modifiers & Modifier.STATIC) != 0)
            classObject.setStatic(true);

        List<Type> superInterfaceTypes = enumDeclaration.superInterfaceTypes();
        for(Type interfaceType : superInterfaceTypes) {
            ITypeBinding binding = interfaceType.resolveBinding();
            String qualifiedName = binding.getQualifiedName();
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            classObject.addInterface(typeObject);
        }

        List<EnumConstantDeclaration> enumConstantDeclarations = enumDeclaration.enumConstants();
        for(EnumConstantDeclaration enumConstantDeclaration : enumConstantDeclarations) {
            EnumConstantDeclarationObject enumConstantDeclarationObject = new EnumConstantDeclarationObject(enumConstantDeclaration.getName().getIdentifier());
            enumConstantDeclarationObject.setEnumName(classObject.getName());
            enumConstantDeclarationObject.setEnumConstantDeclaration(enumConstantDeclaration);
            List<Expression> arguments = enumConstantDeclaration.arguments();
            for(Expression argument : arguments) {
                AbstractExpression abstractExpression = new AbstractExpression(argument);
                enumConstantDeclarationObject.addArgument(abstractExpression);
            }
            classObject.addEnumConstantDeclaration(enumConstantDeclarationObject);
        }

        List<BodyDeclaration> bodyDeclarations = enumDeclaration.bodyDeclarations();
        for(BodyDeclaration bodyDeclaration : bodyDeclarations) {
            if(bodyDeclaration instanceof MethodDeclaration) {
                processMethodDeclaration(classObject, (MethodDeclaration)bodyDeclaration);
            }
            else if(bodyDeclaration instanceof FieldDeclaration) {
                processFieldDeclaration(classObject, (FieldDeclaration)bodyDeclaration);
            }
        }
        return classObject;
    }

    private void processFieldDeclaration(final ClassObject classObject, FieldDeclaration fieldDeclaration) {
        Type fieldType = fieldDeclaration.getType();
        ITypeBinding binding = fieldType.resolveBinding();
        List<CommentObject> fieldDeclarationComments = new ArrayList<CommentObject>();
        int fieldDeclarationStartPosition = fieldDeclaration.getStartPosition();
        int fieldDeclarationEndPosition = fieldDeclarationStartPosition + fieldDeclaration.getLength();
        for (ListIterator<CommentObject> it = classObject.getCommentIterator(); it.hasNext(); ) {
            CommentObject comment = it.next();
            int commentStartPosition = comment.getStartPosition();
            int commentEndPosition = commentStartPosition + comment.getLength();
            if(fieldDeclarationStartPosition <= commentStartPosition && fieldDeclarationEndPosition >= commentEndPosition) {
                fieldDeclarationComments.add(comment);
            }
        }
        List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
        for(VariableDeclarationFragment fragment : fragments) {
            String qualifiedName = binding.getQualifiedName();
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            typeObject.setArrayDimension(typeObject.getArrayDimension() + fragment.getExtraDimensions());
            FieldObject fieldObject = new FieldObject(typeObject, fragment.getName().getIdentifier());
            fieldObject.setClassName(classObject.getName());
            fieldObject.setVariableDeclarationFragment(fragment);
            fieldObject.addComments(fieldDeclarationComments);

            int fieldModifiers = fieldDeclaration.getModifiers();
            if((fieldModifiers & Modifier.PUBLIC) != 0)
                fieldObject.setAccess(Access.PUBLIC);
            else if((fieldModifiers & Modifier.PROTECTED) != 0)
                fieldObject.setAccess(Access.PROTECTED);
            else if((fieldModifiers & Modifier.PRIVATE) != 0)
                fieldObject.setAccess(Access.PRIVATE);
            else
                fieldObject.setAccess(Access.NONE);

            if((fieldModifiers & Modifier.STATIC) != 0)
                fieldObject.setStatic(true);

            classObject.addField(fieldObject);
        }
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOfDot = name.lastIndexOf('.');
        return (lastIndexOfDot == -1) ? "" : name.substring(lastIndexOfDot);
    }

    private void processMethodDeclaration(final ClassObject classObject, MethodDeclaration methodDeclaration) {
        String methodName = methodDeclaration.getName().getIdentifier();
        final ConstructorObject constructorObject = new ConstructorObject();
        constructorObject.setMethodDeclaration(methodDeclaration);
        constructorObject.setName(methodName);
        constructorObject.setClassName(classObject.getName());
        int methodDeclarationStartPosition = methodDeclaration.getStartPosition();
        int methodDeclarationEndPosition = methodDeclarationStartPosition + methodDeclaration.getLength();
        for (ListIterator<CommentObject> it = classObject.getCommentIterator(); it.hasNext(); ) {
            CommentObject comment = it.next();
            int commentStartPosition = comment.getStartPosition();
            int commentEndPosition = commentStartPosition + comment.getLength();
            if(methodDeclarationStartPosition <= commentStartPosition && methodDeclarationEndPosition >= commentEndPosition) {
                constructorObject.addComment(comment);
            }
        }

        if(methodDeclaration.getJavadoc() != null) {
            Javadoc javaDoc = methodDeclaration.getJavadoc();
            List<TagElement> tags = javaDoc.tags();
            for(TagElement tagElement : tags) {
                String tagName = tagElement.getTagName();
                if(tagName != null && tagName.equals(TagElement.TAG_THROWS)) {
                    List<IDocElement> fragments = tagElement.fragments();
                    for(IDocElement docElement : fragments) {
                        if(docElement instanceof Name name) {
                            IBinding binding = name.resolveBinding();
                            if(binding instanceof ITypeBinding typeBinding) {
                                constructorObject.addExceptionInJavaDocThrows(typeBinding.getQualifiedName());
                            }
                        }
                    }
                }
            }
        }
        int methodModifiers = methodDeclaration.getModifiers();
        if((methodModifiers & Modifier.PUBLIC) != 0)
            constructorObject.setAccess(Access.PUBLIC);
        else if((methodModifiers & Modifier.PROTECTED) != 0)
            constructorObject.setAccess(Access.PROTECTED);
        else if((methodModifiers & Modifier.PRIVATE) != 0)
            constructorObject.setAccess(Access.PRIVATE);
        else
            constructorObject.setAccess(Access.NONE);

        List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
        for(SingleVariableDeclaration parameter : parameters) {
            Type parameterType = parameter.getType();
            ITypeBinding binding = parameterType.resolveBinding();
            String qualifiedName = binding.getQualifiedName();
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            typeObject.setArrayDimension(typeObject.getArrayDimension() + parameter.getExtraDimensions());
            if(parameter.isVarargs()) {
                typeObject.setArrayDimension(1);
            }
            ParameterObject parameterObject = new ParameterObject(typeObject, parameter.getName().getIdentifier(), parameter.isVarargs());
            parameterObject.setSingleVariableDeclaration(parameter);
            constructorObject.addParameter(parameterObject);
        }

        Block methodBody = methodDeclaration.getBody();
        if(methodBody != null) {
            MethodBodyObject methodBodyObject = new MethodBodyObject(methodBody);
            constructorObject.setMethodBody(methodBodyObject);
        }

        for(AnonymousClassDeclarationObject anonymous : constructorObject.getAnonymousClassDeclarations()) {
            anonymous.setClassObject(classObject);
            AnonymousClassDeclaration anonymousClassDeclaration = anonymous.getAnonymousClassDeclaration();
            int anonymousClassDeclarationStartPosition = anonymousClassDeclaration.getStartPosition();
            int anonymousClassDeclarationEndPosition = anonymousClassDeclarationStartPosition + anonymousClassDeclaration.getLength();
            for (ListIterator<CommentObject> it = constructorObject.getCommentListIterator(); it.hasNext(); ) {
                CommentObject comment = it.next();
                int commentStartPosition = comment.getStartPosition();
                int commentEndPosition = commentStartPosition + comment.getLength();
                if(anonymousClassDeclarationStartPosition <= commentStartPosition && anonymousClassDeclarationEndPosition >= commentEndPosition) {
                    anonymous.addComment(comment);
                }
            }
        }

        if(methodDeclaration.isConstructor()) {
            classObject.addConstructor(constructorObject);
        }
        else {
            MethodObject methodObject = new MethodObject(constructorObject);
            List<IExtendedModifier> extendedModifiers = methodDeclaration.modifiers();
            for(IExtendedModifier extendedModifier : extendedModifiers) {
                if(extendedModifier.isAnnotation()) {
                    Annotation annotation = (Annotation)extendedModifier;
                    if(annotation.getTypeName().getFullyQualifiedName().equals("Test")) {
                        methodObject.setTestAnnotation(true);
                        break;
                    }
                }
            }
            Type returnType = methodDeclaration.getReturnType2();
            ITypeBinding binding = returnType.resolveBinding();
            String qualifiedName = binding.getQualifiedName();
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            methodObject.setReturnType(typeObject);

            if((methodModifiers & Modifier.ABSTRACT) != 0)
                methodObject.setAbstract(true);
            if((methodModifiers & Modifier.STATIC) != 0)
                methodObject.setStatic(true);
            if((methodModifiers & Modifier.SYNCHRONIZED) != 0)
                methodObject.setSynchronized(true);
            if((methodModifiers & Modifier.NATIVE) != 0)
                methodObject.setNative(true);

            classObject.addMethod(methodObject);
            FieldInstructionObject fieldInstruction = methodObject.isGetter();
            if(fieldInstruction != null)
                systemObject.addGetter(methodObject.generateMethodInvocation(), fieldInstruction);
            fieldInstruction = methodObject.isSetter();
            if(fieldInstruction != null)
                systemObject.addSetter(methodObject.generateMethodInvocation(), fieldInstruction);
            fieldInstruction = methodObject.isCollectionAdder();
            if(fieldInstruction != null)
                systemObject.addCollectionAdder(methodObject.generateMethodInvocation(), fieldInstruction);
            MethodInvocationObject methodInvocation = methodObject.isDelegate();
            if(methodInvocation != null)
                systemObject.addDelegate(methodObject.generateMethodInvocation(), methodInvocation);
        }
    }

    public SystemObject getSystemObject() {
        return systemObject;
    }


    public static AST getAST() {
        if(systemObject.getClassNumber() > 0) {
            return systemObject.getClassObject(0).getAbstractTypeDeclaration().getAST();
        }
        return null;
    }
}
