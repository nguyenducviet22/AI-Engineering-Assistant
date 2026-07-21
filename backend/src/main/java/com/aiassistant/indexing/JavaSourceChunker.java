package com.aiassistant.indexing;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class JavaSourceChunker implements SourceChunker {
    @Override
    public boolean supports(String language) {
        return "Java".equalsIgnoreCase(language);
    }

    @Override
    public List<RepositoryChunk> chunk(String content, ChunkingContext context) {
        AtomicInteger chunkIndex = new AtomicInteger();
        try {
            CompilationUnit unit = StaticJavaParser.parse(content);
            String packageName = unit.getPackageDeclaration().map(declaration -> declaration.getName().asString()).orElse("");
            List<RepositoryChunk> chunks = new ArrayList<>();
            List<TypeDeclaration<?>> types = new ArrayList<>();
            types.addAll(unit.findAll(ClassOrInterfaceDeclaration.class));
            types.addAll(unit.findAll(EnumDeclaration.class));
            types.addAll(unit.findAll(RecordDeclaration.class));
            types.stream()
                    .filter(type -> type.getRange().isPresent())
                    .sorted(Comparator.comparingInt(type -> type.getRange().orElseThrow().begin.line))
                    .forEach(type -> addTypeChunks(content, context, packageName, type, chunks, chunkIndex));
            if (chunks.isEmpty()) {
                chunks.addAll(SemanticChunkFactory.chunks(
                        content, context, packageName, "", "", ChunkType.SOURCE, 1, Math.max(1, LineText.lines(content).size()), chunkIndex));
            }
            return chunks;
        } catch (RuntimeException ex) {
            return SemanticChunkFactory.chunks(
                    content, context, "", "", "", ChunkType.SOURCE, 1, Math.max(1, LineText.lines(content).size()), chunkIndex);
        }
    }

    private void addTypeChunks(
            String content,
            ChunkingContext context,
            String packageName,
            TypeDeclaration<?> type,
            List<RepositoryChunk> chunks,
            AtomicInteger chunkIndex) {
        String className = qualifiedTypeName(type);
        List<String> lines = LineText.lines(content);
        if (type.getRange().isPresent()) {
            var range = type.getRange().orElseThrow();
            String typeContent = LineText.slice(lines, range.begin.line, range.end.line);
            if (LineText.estimatedTokens(typeContent) <= 1200) {
                chunks.addAll(SemanticChunkFactory.chunks(
                        typeContent,
                        context,
                        packageName,
                        className,
                        "",
                        ChunkType.CLASS,
                        range.begin.line,
                        range.end.line,
                        chunkIndex));
            } else {
                int headerEndLine = type.getMembers().stream()
                        .filter(member -> member.getRange().isPresent())
                        .mapToInt(member -> member.getRange().orElseThrow().begin.line - 1)
                        .min()
                        .orElse(Math.min(range.begin.line + 20, range.end.line));
                headerEndLine = Math.max(range.begin.line, Math.min(headerEndLine, range.end.line));
                chunks.addAll(SemanticChunkFactory.chunks(
                        LineText.slice(lines, range.begin.line, headerEndLine),
                        context,
                        packageName,
                        className,
                        "",
                        ChunkType.CLASS,
                        range.begin.line,
                        headerEndLine,
                        chunkIndex));
            }
        }

        List<Node> callables = new ArrayList<>();
        callables.addAll(type.findAll(MethodDeclaration.class));
        callables.addAll(type.findAll(ConstructorDeclaration.class));
        callables.stream()
                .filter(node -> node.getRange().isPresent())
                .filter(node -> node.findAncestor(TypeDeclaration.class).map(type::equals).orElse(false))
                .sorted(Comparator.comparingInt(node -> node.getRange().orElseThrow().begin.line))
                .forEach(node -> node.getRange().ifPresent(range -> chunks.addAll(SemanticChunkFactory.chunks(
                        LineText.slice(lines, range.begin.line, range.end.line),
                        context,
                        packageName,
                        nearestTypeName(node).orElse(className),
                        callableName(node),
                        ChunkType.METHOD,
                        range.begin.line,
                        range.end.line,
                        chunkIndex))));
    }

    private String callableName(Node node) {
        if (node instanceof MethodDeclaration method) {
            return method.getNameAsString();
        }
        if (node instanceof ConstructorDeclaration constructor) {
            return constructor.getNameAsString();
        }
        return "";
    }

    private Optional<String> nearestTypeName(Node node) {
        return node.findAncestor(TypeDeclaration.class).map(this::qualifiedTypeName);
    }

    private String qualifiedTypeName(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().orElse(type.getNameAsString());
    }
}
