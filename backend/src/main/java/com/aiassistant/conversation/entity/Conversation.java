package com.aiassistant.conversation.entity;

import com.aiassistant.workspace.entity.Workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Workspace workspace;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<ConversationMessage> messages = new ArrayList<>();

    protected Conversation() {
    }

    public Conversation(Workspace workspace, String title) {
        this.workspace = workspace;
        this.title = title;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ConversationMessage> getMessages() { return messages; }
}
