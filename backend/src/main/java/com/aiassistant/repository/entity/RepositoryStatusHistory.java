package com.aiassistant.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "repository_status_history")
public class RepositoryStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ProjectRepository repository;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepositoryStatus status;
    private String reason;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RepositoryStatusHistory() {
    }

    public RepositoryStatusHistory(ProjectRepository repository, RepositoryStatus status, String reason) {
        this.repository = repository;
        this.status = status;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public ProjectRepository getRepository() { return repository; }
    public RepositoryStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
