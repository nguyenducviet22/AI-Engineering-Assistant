package com.aiassistant.repository.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "repository_versions")
public class RepositoryVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ProjectRepository repository;
    @Column(nullable = false)
    private Integer version;
    private String commitHash;
    @Column(nullable = false)
    private Instant uploadDate = Instant.now();

    protected RepositoryVersion() {
    }

    public RepositoryVersion(ProjectRepository repository, Integer version) {
        this.repository = repository;
        this.version = version;
    }

    public Long getId() { return id; }
    public ProjectRepository getRepository() { return repository; }
    public Integer getVersion() { return version; }
    public String getCommitHash() { return commitHash; }
    public Instant getUploadDate() { return uploadDate; }
}
