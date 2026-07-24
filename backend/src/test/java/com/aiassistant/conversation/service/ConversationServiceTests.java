package com.aiassistant.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.ai.LlmService;
import com.aiassistant.ai.workflow.RepositoryChatState;
import com.aiassistant.auth.entity.User;
import com.aiassistant.conversation.entity.MessageRole;
import com.aiassistant.conversation.repository.ConversationMessageRepository;
import com.aiassistant.conversation.repository.ConversationRepository;
import com.aiassistant.retrieval.Citation;
import com.aiassistant.retrieval.RetrievalResult;
import com.aiassistant.retrieval.RetrievedChunk;
import com.aiassistant.workspace.entity.Workspace;
import com.aiassistant.workspace.entity.WorkspaceVisibility;
import com.aiassistant.workspace.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ConversationServiceTests {
    @Autowired
    private ConversationRepository conversations;
    @Autowired
    private ConversationMessageRepository messages;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsRepositoryChatTurnWithCitationsAndRetrievalMetadata() {
        User owner = entityManager.persistFlushFind(new User("owner@example.com", "hash", "Owner"));
        Workspace workspace = entityManager.persistFlushFind(new Workspace(
                owner,
                "Backend",
                "Spring app",
                "Java",
                "Spring Boot",
                WorkspaceVisibility.PRIVATE
        ));
        WorkspaceService workspaceService = Mockito.mock(WorkspaceService.class);
        when(workspaceService.requireExisting(workspace.getId())).thenReturn(workspace);
        ConversationService service = new ConversationService(conversations, messages, workspaceService, new ObjectMapper());
        RepositoryChatState state = RepositoryChatState.start(workspace.getId(), null, "Where is JWT validated?", List.of())
                .withRetrievalResult(new RetrievalResult(List.of(
                        new RetrievedChunk("security-config", "src/main/java/SecurityConfig.java", 12, 48, "SecurityConfig", "JWT config", 0.91)
                )))
                .withLlmResponse(new LlmService.LlmResponse("JWT is validated in security. [SecurityConfig.java:12-48]", "openai/gpt-4.1", 64))
                .withCitations(List.of(new Citation("security-config", "src/main/java/SecurityConfig.java", 12, 48, 30, 57, "[SecurityConfig.java:12-48]")));

        ConversationPersistencePort.PersistedConversationTurn persisted = service.persist(state);

        verify(workspaceService).requireExisting(workspace.getId());
        assertThat(persisted.conversationId()).isNotNull();
        assertThat(persisted.assistantMessageId()).isNotNull();
        assertThat(conversations.findById(persisted.conversationId())).get().satisfies(conversation -> {
            assertThat(conversation.getWorkspace().getId()).isEqualTo(workspace.getId());
            assertThat(conversation.getTitle()).isEqualTo("Where is JWT validated?");
        });
        assertThat(messages.findAllByConversationIdOrderByCreatedAtAsc(persisted.conversationId()))
                .hasSize(2)
                .satisfiesExactly(
                        userMessage -> {
                            assertThat(userMessage.getRole()).isEqualTo(MessageRole.USER);
                            assertThat(userMessage.getContent()).isEqualTo("Where is JWT validated?");
                        },
                        assistantMessage -> {
                            assertThat(assistantMessage.getRole()).isEqualTo(MessageRole.ASSISTANT);
                            assertThat(assistantMessage.getContent()).contains("JWT is validated");
                            assertThat(assistantMessage.getModel()).isEqualTo("openai/gpt-4.1");
                            assertThat(assistantMessage.getTokenUsage()).isEqualTo(64);
                            assertThat(assistantMessage.getCitationsJson()).contains("security-config");
                            assertThat(assistantMessage.getRetrievalMetadataJson()).contains("JWT config");
                        }
                );
    }

    @Test
    void persistsRefusedTurnWithRefusalMessageAndEmptyCitations() {
        User owner = entityManager.persistFlushFind(new User("refused@example.com", "hash", "Owner"));
        Workspace workspace = entityManager.persistFlushFind(new Workspace(
                owner,
                "Backend",
                "Spring app",
                "Java",
                "Spring Boot",
                WorkspaceVisibility.PRIVATE
        ));
        WorkspaceService workspaceService = Mockito.mock(WorkspaceService.class);
        when(workspaceService.requireExisting(workspace.getId())).thenReturn(workspace);
        ConversationService service = new ConversationService(conversations, messages, workspaceService, new ObjectMapper());
        RepositoryChatState refused = RepositoryChatState.start(workspace.getId(), null, "Who owns billing?", List.of())
                .withRetrievalResult(RetrievalResult.empty())
                .withRefusal(RepositoryChatState.INSUFFICIENT_CONTEXT_MESSAGE)
                .withCitations(List.of());

        ConversationPersistencePort.PersistedConversationTurn persisted = service.persist(refused);

        assertThat(messages.findAllByConversationIdOrderByCreatedAtAsc(persisted.conversationId()))
                .hasSize(2)
                .satisfiesExactly(
                        userMessage -> assertThat(userMessage.getRole()).isEqualTo(MessageRole.USER),
                        assistantMessage -> {
                            assertThat(assistantMessage.getRole()).isEqualTo(MessageRole.ASSISTANT);
                            assertThat(assistantMessage.getContent()).isEqualTo(RepositoryChatState.INSUFFICIENT_CONTEXT_MESSAGE);
                            assertThat(assistantMessage.getCitationsJson()).isEqualTo("[]");
                            assertThat(assistantMessage.getRetrievalMetadataJson()).isEqualTo("[]");
                            assertThat(assistantMessage.getModel()).isNull();
                            assertThat(assistantMessage.getTokenUsage()).isNull();
                        }
                );
    }
}
