package com.aiassistant.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticChunkingServiceTests {
    @Test
    void javaChunkingPreservesClassAndMethodBoundaries() {
        JavaSourceChunker chunker = new JavaSourceChunker();
        String source = """
                package com.example.auth;

                public class AuthService {
                    private final String issuer = "test";

                    public String login(String email) {
                        String normalized = email.trim().toLowerCase();
                        return normalized + issuer;
                    }

                    public void logout() {
                        System.out.println("bye");
                    }
                }
                """;

        List<RepositoryChunk> chunks = chunker.chunk(source, context("Java", "src/main/java/com/example/auth/AuthService.java"));

        RepositoryChunk classChunk = chunks.stream()
                .filter(chunk -> chunk.chunkType() == ChunkType.CLASS && chunk.className().equals("com.example.auth.AuthService"))
                .findFirst()
                .orElseThrow();
        RepositoryChunk loginChunk = chunks.stream()
                .filter(chunk -> chunk.chunkType() == ChunkType.METHOD && chunk.methodName().equals("login"))
                .findFirst()
                .orElseThrow();
        RepositoryChunk logoutChunk = chunks.stream()
                .filter(chunk -> chunk.chunkType() == ChunkType.METHOD && chunk.methodName().equals("logout"))
                .findFirst()
                .orElseThrow();

        assertThat(classChunk.startLine()).isEqualTo(3);
        assertThat(classChunk.endLine()).isEqualTo(14);
        assertThat(loginChunk.startLine()).isEqualTo(6);
        assertThat(loginChunk.endLine()).isEqualTo(9);
        assertThat(loginChunk.content()).contains("public String login").doesNotContain("public void logout");
        assertThat(logoutChunk.startLine()).isEqualTo(11);
        assertThat(logoutChunk.endLine()).isEqualTo(13);
        assertThat(logoutChunk.content()).contains("public void logout").doesNotContain("public String login");
    }

    @Test
    void allChunksExposeRequiredMetadata() {
        SemanticChunkingService service = new SemanticChunkingService(List.of(
                new JavaSourceChunker(),
                new ScriptSourceChunker(),
                new SqlSourceChunker(),
                new MarkdownSourceChunker(),
                new StructuredTextSourceChunker()));

        List<RepositoryChunk> chunks = service.chunk("""
                package com.example;
                class Demo {
                    void work() {
                        System.out.println("ok");
                    }
                }
                """, context("Java", "src/main/java/com/example/Demo.java"));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.hasRequiredMetadata()).isTrue();
            assertThat(chunk.workspaceId()).isEqualTo(10L);
            assertThat(chunk.repositoryId()).isEqualTo(20L);
            assertThat(chunk.version()).isEqualTo(3);
            assertThat(chunk.language()).isEqualTo("Java");
            assertThat(chunk.framework()).isEqualTo("Spring Boot");
            assertThat(chunk.filePath()).isEqualTo("src/main/java/com/example/Demo.java");
            assertThat(chunk.startLine()).isPositive();
            assertThat(chunk.endLine()).isGreaterThanOrEqualTo(chunk.startLine());
        });
    }

    @Test
    void scriptChunkingFindsClassAndFunctionBlocks() {
        ScriptSourceChunker chunker = new ScriptSourceChunker();
        String source = """
                export class UserCard {
                  render() {
                    return "user";
                  }
                }

                export const useUser = async (id: string) => {
                  return fetch(`/users/${id}`);
                };
                """;

        List<RepositoryChunk> chunks = chunker.chunk(source, context("TypeScript", "src/UserCard.tsx"));

        assertThat(chunks)
                .anySatisfy(chunk -> assertThat(chunk.className()).isEqualTo("UserCard"))
                .anySatisfy(chunk -> {
                    assertThat(chunk.methodName()).isEqualTo("useUser");
                    assertThat(chunk.content()).contains("fetch").doesNotContain("export class UserCard");
                });
    }

    private ChunkingContext context(String language, String filePath) {
        return new ChunkingContext(10L, 20L, 3, "Spring Boot", filePath, language);
    }
}
