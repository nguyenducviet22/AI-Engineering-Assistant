package com.aiassistant.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiassistant.ai.LlmService;
import com.aiassistant.ai.PromptBuilder;
import com.aiassistant.ai.PromptProperties;
import com.aiassistant.ai.PromptTemplateService;
import com.aiassistant.conversation.service.ConversationRetrievalContext;
import com.aiassistant.retrieval.Citation;
import com.aiassistant.retrieval.RepositoryRetrievalService;
import com.aiassistant.retrieval.RetrievalResult;
import com.aiassistant.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RepositoryChatWorkflowTests {
    @Test
    void workflowExecutesNodesInExactDesignOrderWhenContextIsValid() {
        AtomicInteger llmCalls = new AtomicInteger();
        RepositoryChatWorkflow workflow = workflow(
                new RetrievalResult(List.of(
                        chunk("1", "AuthController.java", 10, 30, "AuthController.login", 0.82),
                        chunk("2", "AuthService.java", 15, 42, "AuthService.authenticate", 0.76)
                )),
                prompt -> {
                    llmCalls.incrementAndGet();
                    return new LlmService.LlmResponse("JWT is validated in the authentication flow. [AuthController.java:10-30]", "test-model", 42);
                });

        RepositoryChatState result = workflow.run(7L, 11L, "Explain JWT validation", List.of());

        assertThat(result.executionTrace()).containsExactly(
                WorkflowStep.START,
                WorkflowStep.INTENT_DETECTION,
                WorkflowStep.RETRIEVE_CONTEXT,
                WorkflowStep.CONTEXT_VALIDATION,
                WorkflowStep.PROMPT_SELECTION,
                WorkflowStep.LLM_GENERATION,
                WorkflowStep.OUTPUT_VALIDATION,
                WorkflowStep.CITATION_MAPPING,
                WorkflowStep.PERSIST_CONVERSATION,
                WorkflowStep.END
        );
        assertThat(result.refused()).isFalse();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().getFirst().filePath()).isEqualTo("AuthController.java");
        assertThat(llmCalls).hasValue(1);
    }

    @Test
    void insufficientContextRefusesAndDoesNotCallLlmService() {
        AtomicInteger llmCalls = new AtomicInteger();
        RepositoryChatWorkflow workflow = workflow(
                new RetrievalResult(List.of(chunk("low", "README.md", 1, 4, "README", 0.12))),
                prompt -> {
                    llmCalls.incrementAndGet();
                    return new LlmService.LlmResponse("This must not be called.", "test-model", 1);
                });

        RepositoryChatState result = workflow.run(7L, null, "Where is JWT validated?", List.of());

        assertThat(result.executionTrace()).containsExactly(
                WorkflowStep.START,
                WorkflowStep.INTENT_DETECTION,
                WorkflowStep.RETRIEVE_CONTEXT,
                WorkflowStep.CONTEXT_VALIDATION,
                WorkflowStep.PROMPT_SELECTION,
                WorkflowStep.LLM_GENERATION,
                WorkflowStep.OUTPUT_VALIDATION,
                WorkflowStep.CITATION_MAPPING,
                WorkflowStep.PERSIST_CONVERSATION,
                WorkflowStep.END
        );
        assertThat(result.refused()).isTrue();
        assertThat(result.answer()).isEqualTo(RepositoryChatState.INSUFFICIENT_CONTEXT_MESSAGE);
        assertThat(result.citations()).isEmpty();
        assertThat(result.prompt()).isNull();
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void followUpQuestionUsesPriorSuccessfulTurnForRetrievalQuery() {
        List<String> capturedQueries = new java.util.ArrayList<>();
        RepositoryRetrievalService retrievalService = query -> {
            capturedQueries.add(query.question());
            if (query.question().contains("JwtAuthenticationFilter") && query.question().contains("Can you explain that in more detail?")) {
                return new RetrievalResult(List.of(
                        chunk("jwt-filter", "JwtAuthenticationFilter.java", 15, 41, "JwtAuthenticationFilter.doFilterInternal", 0.42),
                        chunk("jwt-util", "JwtUtil.java", 10, 30, "JwtUtil.validateToken", 0.32),
                        chunk("security", "SecurityConfig.java", 20, 45, "SecurityConfig", 0.27)
                ));
            }
            return new RetrievalResult(List.of(chunk("weak-follow-up", "README.md", 1, 4, "README", 0.25)));
        };
        RepositoryChatWorkflow workflow = workflow(
                retrievalService,
                prompt -> new LlmService.LlmResponse(
                        "The filter reads a Bearer token, validates it, and sets authentication. [JwtAuthenticationFilter.java:15-41]",
                        "test-model",
                        80));

        RepositoryChatState result = workflow.run(
                7L,
                11L,
                "Can you explain that in more detail?",
                List.of(
                        new PromptBuilder.ConversationTurn("USER", "How does JwtAuthenticationFilter authenticate a request?"),
                        new PromptBuilder.ConversationTurn("ASSISTANT", "It validates the bearer token.")
                ),
                Optional.of(new ConversationRetrievalContext(
                        "How does JwtAuthenticationFilter authenticate a request?",
                        "It reads the Authorization header and validates the JWT before setting authentication.",
                        List.of(new Citation("jwt-filter", "JwtAuthenticationFilter.java", 15, 41))
                ))
        );

        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0)).isEqualTo("Can you explain that in more detail?");
        assertThat(capturedQueries.get(1))
                .contains("Previous successful user question:")
                .contains("How does JwtAuthenticationFilter authenticate a request?")
                .contains("Previous successful assistant answer:")
                .contains("JwtAuthenticationFilter.java:15-41")
                .contains("Current user message:")
                .contains("Can you explain that in more detail?");
        assertThat(result.refused()).isFalse();
        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.chunkId()).isEqualTo("jwt-filter");
            assertThat(citation.filePath()).isEqualTo("JwtAuthenticationFilter.java");
        });
    }

    @Test
    void veryShortFollowUpCanUseCondensedRetrievalWhenRawScoreIsInFollowUpBand() {
        List<String> capturedQueries = new java.util.ArrayList<>();
        RepositoryRetrievalService retrievalService = query -> {
            capturedQueries.add(query.question());
            if (query.question().contains("JwtAuthenticationFilter") && query.question().contains("why?")) {
                return new RetrievalResult(List.of(
                        chunk("jwt-filter", "JwtAuthenticationFilter.java", 15, 41, "JwtAuthenticationFilter.doFilterInternal", 0.50)
                ));
            }
            return new RetrievalResult(List.of(chunk("weak-short-follow-up", "README.md", 1, 4, "README", 0.24)));
        };
        RepositoryChatWorkflow workflow = workflow(
                retrievalService,
                prompt -> new LlmService.LlmResponse(
                        "The filter authenticates only when the token is valid. [JwtAuthenticationFilter.java:15-41]",
                        "test-model",
                        80));

        RepositoryChatState result = workflow.run(
                7L,
                11L,
                "why?",
                List.of(),
                Optional.of(jwtRetrievalContext())
        );

        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0)).isEqualTo("why?");
        assertThat(capturedQueries.get(1)).contains("JwtAuthenticationFilter").contains("why?");
        assertThat(result.refused()).isFalse();
        assertThat(result.citations()).singleElement().satisfies(citation ->
                assertThat(citation.filePath()).isEqualTo("JwtAuthenticationFilter.java"));
    }

    @Test
    void unrelatedFollowUpPhraseWithVeryLowRawScoreDoesNotUseCondensedRetrieval() {
        AtomicInteger llmCalls = new AtomicInteger();
        List<String> capturedQueries = new java.util.ArrayList<>();
        RepositoryRetrievalService retrievalService = query -> {
            capturedQueries.add(query.question());
            return new RetrievalResult(List.of(chunk("unrelated", "README.md", 1, 4, "README", 0.12)));
        };
        RepositoryChatWorkflow workflow = workflow(
                retrievalService,
                prompt -> {
                    llmCalls.incrementAndGet();
                    return new LlmService.LlmResponse("This must not be called.", "test-model", 1);
                });

        RepositoryChatState result = workflow.run(
                7L,
                11L,
                "Can you explain that in more detail about the capital of France?",
                List.of(),
                Optional.of(jwtRetrievalContext())
        );

        assertThat(capturedQueries).containsExactly("Can you explain that in more detail about the capital of France?");
        assertThat(result.refused()).isTrue();
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void standaloneQuestionThatPassesRawRetrievalDoesNotUseCondensationEvenWithHistory() {
        List<String> capturedQueries = new java.util.ArrayList<>();
        RepositoryRetrievalService retrievalService = query -> {
            capturedQueries.add(query.question());
            return new RetrievalResult(List.of(
                    chunk("auth", "AuthService.java", 10, 40, "AuthService.login", 0.50)
            ));
        };
        RepositoryChatWorkflow workflow = workflow(
                retrievalService,
                prompt -> new LlmService.LlmResponse("Login checks credentials. [AuthService.java:10-40]", "test-model", 34));

        RepositoryChatState result = workflow.run(
                7L,
                11L,
                "What does AuthService.login check before issuing a token?",
                List.of(),
                Optional.of(jwtRetrievalContext())
        );

        assertThat(capturedQueries).containsExactly("What does AuthService.login check before issuing a token?");
        assertThat(result.refused()).isFalse();
    }

    @Test
    void firstMessageRetrievalQueryIsUnchangedWhenNoConversationContextExists() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        RepositoryRetrievalService retrievalService = query -> {
            capturedQuery.set(query.question());
            return new RetrievalResult(List.of(
                    chunk("auth", "AuthService.java", 10, 40, "AuthService.login", 0.50)
            ));
        };
        RepositoryChatWorkflow workflow = workflow(
                retrievalService,
                prompt -> new LlmService.LlmResponse("Login checks credentials. [AuthService.java:10-40]", "test-model", 34));

        RepositoryChatState result = workflow.run(7L, null, "What does AuthService.login check?", List.of());

        assertThat(capturedQuery.get()).isEqualTo("What does AuthService.login check?");
        assertThat(result.refused()).isFalse();
    }

    @Test
    void queryCondensationGateRequiresHistoryFollowUpShapeAndRawScoreFloor() {
        QueryCondensationService service = new QueryCondensationService();
        Optional<ConversationRetrievalContext> prior = Optional.of(jwtRetrievalContext());

        assertThat(service.shouldAttemptCondensation("why?", rawResult(0.24), prior)).isTrue();
        assertThat(service.shouldAttemptCondensation("go on", rawResult(0.23), prior)).isTrue();
        assertThat(service.shouldAttemptCondensation("and then?", rawResult(0.26), prior)).isTrue();
        assertThat(service.shouldAttemptCondensation("Can you explain that in more detail?", rawResult(0.25), prior)).isTrue();
        assertThat(service.shouldAttemptCondensation("Can you explain that in more detail about the capital of France?", rawResult(0.12), prior))
                .isFalse();
        assertThat(service.shouldAttemptCondensation("What does AuthService.login check?", rawResult(0.25), prior)).isFalse();
        assertThat(service.shouldAttemptCondensation("why?", rawResult(0.24), Optional.empty())).isFalse();
    }

    @Test
    void contextValidationAcceptsStrongSingleChunk() {
        RepositoryChatState state = RepositoryChatState.start(1L, null, "Explain login", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(chunk("strong", "AuthService.java", 1, 20, "AuthService", 0.50))));

        RepositoryChatState result = new ContextValidationNode().apply(state);

        assertThat(result.contextValid()).isTrue();
        assertThat(result.refused()).isFalse();
    }

    @Test
    void contextValidationUsesInclusiveThresholds() {
        RepositoryChatState exactBoundary = RepositoryChatState.start(1L, null, "Explain login", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        chunk("top", "AuthController.java", 1, 10, "AuthController", 0.32),
                        chunk("mid", "AuthService.java", 11, 20, "AuthService", 0.27),
                        chunk("low", "SecurityConfig.java", 21, 30, "SecurityConfig", 0.22)
                )));
        RepositoryChatState justUnder = RepositoryChatState.start(1L, null, "Explain login", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        chunk("top", "AuthController.java", 1, 10, "AuthController", 0.31),
                        chunk("mid", "AuthService.java", 11, 20, "AuthService", 0.27),
                        chunk("low", "SecurityConfig.java", 21, 30, "SecurityConfig", 0.23)
                )));

        ContextValidationNode node = new ContextValidationNode();

        assertThat(node.apply(exactBoundary).contextValid()).isTrue();
        RepositoryChatState refused = node.apply(justUnder);
        assertThat(refused.contextValid()).isFalse();
        assertThat(refused.refused()).isTrue();
    }

    @Test
    void contextValidationRefusesClearlyIrrelevantScoreLevel() {
        RepositoryChatState state = RepositoryChatState.start(1L, null, "What is the capital of France?", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        chunk("unrelated-1", "User.java", 44, 44, "User.getFullName", 0.12),
                        chunk("unrelated-2", "Workspace.java", 58, 58, "Workspace.getFramework", 0.11),
                        chunk("unrelated-3", "CodeChunk.java", 90, 90, "CodeChunk.getFramework", 0.10)
                )));

        RepositoryChatState result = new ContextValidationNode().apply(state);

        assertThat(result.contextValid()).isFalse();
        assertThat(result.refused()).isTrue();
    }

    @Test
    void citationMappingMapsResponseSpanToChunkFileAndLineRange() {
        RepositoryChatState state = RepositoryChatState.start(1L, null, "Explain login", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        chunk("auth-controller", "src/main/java/AuthController.java", 10, 40, "AuthController.login", 0.91),
                        chunk("auth-service", "src/main/java/AuthService.java", 20, 70, "AuthService.authenticate", 0.88)
                )))
                .withAnswer("Login starts in the controller [AuthController.java:12-30] and delegates to the service [chunk:auth-service].");

        RepositoryChatState result = new CitationMappingNode().apply(state);

        assertThat(result.citations()).hasSize(2);
        Citation controller = result.citations().get(0);
        assertThat(controller.chunkId()).isEqualTo("auth-controller");
        assertThat(controller.filePath()).isEqualTo("src/main/java/AuthController.java");
        assertThat(controller.startLine()).isEqualTo(12);
        assertThat(controller.endLine()).isEqualTo(30);
        assertThat(controller.responseText()).isEqualTo("[AuthController.java:12-30]");
        assertThat(controller.responseStartIndex()).isGreaterThanOrEqualTo(0);

        Citation service = result.citations().get(1);
        assertThat(service.chunkId()).isEqualTo("auth-service");
        assertThat(service.filePath()).isEqualTo("src/main/java/AuthService.java");
        assertThat(service.startLine()).isEqualTo(20);
        assertThat(service.endLine()).isEqualTo(70);
        assertThat(service.responseText()).isEqualTo("[chunk:auth-service]");
    }

    @Test
    void citationMappingUsesPromptBuilderEvidenceMetadataForResponseSpan() {
        PromptBuilder promptBuilder = new PromptBuilder(
                new PromptTemplateService(),
                new PromptProperties(24000, 14000, 6000, 8));
        PromptBuilder.BuiltPrompt prompt = promptBuilder.buildRepositoryChatPrompt(new PromptBuilder.RepositoryChatPromptRequest(
                "Where is JWT validated?",
                List.of(new PromptBuilder.RetrievedContextBlock(
                        "C1",
                        "security-config",
                        "src/main/java/com/example/SecurityConfig.java",
                        12,
                        48,
                        "SecurityConfig",
                        "JWT validation configuration.",
                        0.91
                )),
                List.of()
        ));
        assertThat(prompt.renderedPrompt())
                .contains("<untrusted_repository_evidence")
                .contains("chunk_id=\"security-config\"")
                .contains("file=\"src/main/java/com/example/SecurityConfig.java\"")
                .contains("lines=\"12-48\"");

        RepositoryChatState state = RepositoryChatState.start(1L, null, "Where is JWT validated?", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        chunk("security-config", "src/main/java/com/example/SecurityConfig.java", 12, 48, "SecurityConfig", 0.91)
                )))
                .withAnswer("JWT validation is configured in security. [SecurityConfig.java:12-48]");

        RepositoryChatState result = new CitationMappingNode().apply(state);

        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.chunkId()).isEqualTo("security-config");
            assertThat(citation.filePath()).isEqualTo("src/main/java/com/example/SecurityConfig.java");
            assertThat(citation.startLine()).isEqualTo(12);
            assertThat(citation.endLine()).isEqualTo(48);
            assertThat(citation.responseText()).isEqualTo("[SecurityConfig.java:12-48]");
            assertThat(citation.responseStartIndex()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void citationMappingAcceptsObservedCommaLinesNearMissFormat() {
        RepositoryChatState state = RepositoryChatState.start(1L, null, "Explain JWT filter", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        chunk(
                                "jwt-filter",
                                "src/main/java/com/aiassistant/auth/security/JwtAuthenticationFilter.java",
                                15,
                                41,
                                "JwtAuthenticationFilter",
                                0.91)
                )))
                .withAnswer("The filter parses bearer tokens [src/main/java/com/aiassistant/auth/security/JwtAuthenticationFilter.java, lines 15-41].");

        RepositoryChatState result = new CitationMappingNode().apply(state);

        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.chunkId()).isEqualTo("jwt-filter");
            assertThat(citation.filePath()).isEqualTo("src/main/java/com/aiassistant/auth/security/JwtAuthenticationFilter.java");
            assertThat(citation.startLine()).isEqualTo(15);
            assertThat(citation.endLine()).isEqualTo(41);
            assertThat(citation.responseText())
                    .isEqualTo("[src/main/java/com/aiassistant/auth/security/JwtAuthenticationFilter.java, lines 15-41]");
        });
    }

    private RepositoryChatWorkflow workflow(RetrievalResult retrievalResult, LlmService llmService) {
        return workflow(query -> retrievalResult, llmService);
    }

    private RepositoryChatWorkflow workflow(RepositoryRetrievalService retrievalService, LlmService llmService) {
        PromptBuilder promptBuilder = new PromptBuilder(
                new PromptTemplateService(),
                new PromptProperties(24000, 14000, 6000, 8));
        return new RepositoryChatWorkflow(
                new IntentDetectionNode(),
                new RetrieveContextNode(retrievalService),
                new ContextValidationNode(),
                new PromptSelectionNode(promptBuilder),
                new LlmGenerationNode(llmService),
                new OutputValidationNode(),
                new CitationMappingNode(),
                new PersistConversationNode()
        );
    }

    private RetrievedChunk chunk(String id, String file, int startLine, int endLine, String symbol, double score) {
        return new RetrievedChunk(id, file, startLine, endLine, symbol, "content for " + symbol, score);
    }

    private RetrievalResult rawResult(double score) {
        return new RetrievalResult(List.of(chunk("raw", "README.md", 1, 4, "README", score)));
    }

    private ConversationRetrievalContext jwtRetrievalContext() {
        return new ConversationRetrievalContext(
                "How does JwtAuthenticationFilter authenticate a request?",
                "It reads the Authorization header and validates the JWT before setting authentication.",
                List.of(new Citation("jwt-filter", "JwtAuthenticationFilter.java", 15, 41))
        );
    }
}
