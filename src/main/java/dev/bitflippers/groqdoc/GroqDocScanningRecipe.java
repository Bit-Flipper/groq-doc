package dev.bitflippers.groqdoc;

import dev.bitflippers.groqdoc.model.Message;
import dev.bitflippers.groqdoc.model.Role;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration.Kind.Type;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class GroqDocScanningRecipe extends ScanningRecipe<Map<String, List<String>>> {
    private static final Groq GROQ = new Groq();

    private static final Message SYSTEM_MESSAGE = new Message(
            Role.SYSTEM,
            "You have been hired as a Javadoc writer. The user will send you a java interface and you will " +
                    "write the Javadoc for the methods. There will be some extra examples of classes that implement the " +
                    "interface that you can use to give additional context when writing the JavaDoc. Do not respond with " +
                    "anything other than a pure Javadoc string. Let me repeat, do not send anything other than a pure " +
                    "Javadoc string, including any and all markdown formatting."
    );

    private static final String JAVA_DOCS_KEY = GroqDoc.class.getTypeName() + ".JAVA_DOCS";

    @Override
    public String getDisplayName() {
        return "Groq Doc";
    }

    @Override
    public String getDescription() {
        return "Get Groq to write JavaDoc for you.";
    }

    @Override
    public Map<String, List<String>> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, List<String>> acc) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                classDecl = super.visitClassDeclaration(classDecl, executionContext);
                if (classDecl.getImplements() == null || classDecl.getImplements().isEmpty()) {
                    return classDecl;
                }

                var implementsInterface = classDecl.getImplements().getFirst();

                J.ClassDeclaration finalClassDecl = classDecl;
                acc.compute(implementsInterface.getType().toString(), (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }

                    v.add(finalClassDecl.print(getCursor()));
                    return v;
                });
                return classDecl;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, List<String>> acc) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                if (!Type.Interface.equals(classDecl.getKind())) {
                    return classDecl;
                }

                J.CompilationUnit compilationUnit = getCursor()
                        .dropParentUntil(p -> p instanceof J.CompilationUnit).getValue();
                String interfaceString = compilationUnit.print(getCursor());
                List<String> classDeclContext = acc.get(classDecl.getType().getFullyQualifiedName());

                String generatedJavaDoc = generateJavadoc(interfaceString, classDeclContext);
                Map<J.MethodDeclaration, Javadoc.DocComment> docs = extractGeneratedJavadocComments(executionContext, generatedJavaDoc);

                getCursor().putMessage(JAVA_DOCS_KEY, docs);

                return super.visitClassDeclaration(classDecl, executionContext);
            }

            private String generateJavadoc(String interfaceString, List<String> classDeclContext) {
                if (classDeclContext == null) {
                    classDeclContext = new ArrayList<>();
                }

                classDeclContext.add(interfaceString);
                var messages = classDeclContext.stream()
                        .map(message -> new Message(Role.USER, message))
                        .collect(Collectors.toList());
                messages.addFirst(SYSTEM_MESSAGE);
                var maybeChoice = GROQ.createCompletion(messages);

                if (maybeChoice.isEmpty()) {
                    return "/* There was an error generating this Javadoc. */";
                }

                return maybeChoice.get().getFirst().message().content();
            }

            private Map<J.MethodDeclaration, Javadoc.DocComment> extractGeneratedJavadocComments(ExecutionContext executionContext, String generatedJavaDoc) {
                SourceFile lst = JavaParser.fromJavaVersion()
                        .build()
                        .parse(executionContext, generatedJavaDoc)
                        .toList()
                        .getFirst();
                ExtractJavadocs javaDocVisitor = new ExtractJavadocs();

                javaDocVisitor.visit(lst, executionContext);
                return javaDocVisitor.JAVA_DOC_COMMENTS;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                method = super.visitMethodDeclaration(method, executionContext);
                if (!method.getComments().isEmpty()) {
                    return method;
                }

                Map<J.MethodDeclaration, Javadoc.DocComment> map = getCursor().getNearestMessage(JAVA_DOCS_KEY);
                final J.MethodDeclaration finalMethod = method;

                List<Comment> docComments = map.entrySet().stream()
                        // Must compare method signatures, .equals() won't work. The J.MethodDeclaration have different UUIDs
                        .filter(entrySet -> doMethodDeclarationsHaveSameSignature(entrySet.getKey(), finalMethod))
                        .map(entrySet -> (Comment) entrySet.getValue())
                        .toList();

                return method.withComments(docComments);
            }

            private boolean doMethodDeclarationsHaveSameSignature(J.MethodDeclaration m1, J.MethodDeclaration m2) {
                if (m1 == null || m2 == null) {
                    return false;
                }

                String signature1 = computeMethodDeclarationSignature(m1);
                String signature2 = computeMethodDeclarationSignature(m2);
                return signature1.equals(signature2);
            }

            private String computeMethodDeclarationSignature(J.MethodDeclaration method) {
                return method.getSimpleName() + method.getParameters()
                        .stream()
                        .map(this::methodParameterToString)
                        .collect(Collectors.joining(", ", "(", ")"));
            }

            private String methodParameterToString(Statement statement) {
                if (statement instanceof J.VariableDeclarations variableDeclarations) {
                    return variableDeclarations.getTypeExpression().toString();
                } else if (statement instanceof J.Empty) {
                    return "";
                }

                throw new IllegalStateException("Unknown statement type: " + statement.getClass());
            }
        };
    }

    private static final class ExtractJavadocs extends JavaIsoVisitor<ExecutionContext> {
        public final Map<J.MethodDeclaration, Javadoc.DocComment> JAVA_DOC_COMMENTS = new HashMap<>();

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            method =  super.visitMethodDeclaration(method, executionContext);
            if (method.getComments().isEmpty() || !(method.getComments().getFirst() instanceof Javadoc.DocComment)) {
                return method;
            }

            JAVA_DOC_COMMENTS.put(method, (Javadoc.DocComment) method.getComments().getFirst());
            return method;
        }
    }
}
