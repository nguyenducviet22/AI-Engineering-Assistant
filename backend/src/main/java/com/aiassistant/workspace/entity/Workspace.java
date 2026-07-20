package com.aiassistant.workspace.entity;

import com.aiassistant.auth.entity.User;
import com.aiassistant.repository.entity.ProjectRepository;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workspaces")
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User owner;
    @Column(nullable = false)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private String framework;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkspaceVisibility visibility;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectRepository> repositories = new ArrayList<>();

    protected Workspace() {
    }

    public Workspace(User owner, String name, String description, String language, String framework, WorkspaceVisibility visibility) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.language = language;
        this.framework = framework;
        this.visibility = visibility;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLanguage() { return language; }
    public String getFramework() { return framework; }
    public WorkspaceVisibility getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String description, String language, String framework, WorkspaceVisibility visibility) {
        this.name = name;
        this.description = description;
        this.language = language;
        this.framework = framework;
        this.visibility = visibility;
    }
}
