package com.aiassistant.conversation.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Conversation conversation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(columnDefinition = "text")
    private String citationsJson;
    @Column(columnDefinition = "text")
    private String retrievalMetadataJson;
    private String model;
    private Integer tokenUsage;
    private Long latencyMillis;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ConversationMessage() {
    }

    public ConversationMessage(Conversation conversation,
                               MessageRole role,
                               String content,
                               String citationsJson,
                               String retrievalMetadataJson,
                               String model,
                               Integer tokenUsage,
                               Long latencyMillis) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
        this.retrievalMetadataJson = retrievalMetadataJson;
        this.model = model;
        this.tokenUsage = tokenUsage;
        this.latencyMillis = latencyMillis;
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getCitationsJson() { return citationsJson; }
    public String getRetrievalMetadataJson() { return retrievalMetadataJson; }
    public String getModel() { return model; }
    public Integer getTokenUsage() { return tokenUsage; }
    public Long getLatencyMillis() { return latencyMillis; }
    public Instant getCreatedAt() { return createdAt; }
}
