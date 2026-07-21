package com.aiassistant.repository.entity;

import com.aiassistant.workspace.entity.Workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repositories")
public class ProjectRepository {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Workspace workspace;
    @Column(nullable = false)
    private String repositoryName;
    private String language;
    private String framework;
    private String buildTool;
    private String packageManager;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepositoryStatus status = RepositoryStatus.UPLOADING;
    private Integer currentVersion = 0;
    private Integer fileCount = 0;
    private Long repositorySize = 0L;
    private String failureReason;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepositoryVersion> versions = new ArrayList<>();

    protected ProjectRepository() {
    }

    public ProjectRepository(Workspace workspace, String repositoryName) {
        this.workspace = workspace;
        this.repositoryName = repositoryName;
    }

    public Long getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public String getRepositoryName() { return repositoryName; }
    public String getLanguage() { return language; }
    public String getFramework() { return framework; }
    public String getBuildTool() { return buildTool; }
    public String getPackageManager() { return packageManager; }
    public RepositoryStatus getStatus() { return status; }
    public Integer getCurrentVersion() { return currentVersion; }
    public Integer getFileCount() { return fileCount; }
    public Long getRepositorySize() { return repositorySize; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public List<RepositoryVersion> getVersions() { return versions; }

    public void markValidating() {
        this.status = RepositoryStatus.VALIDATING;
        this.failureReason = null;
    }

    public void markValidated(RepositoryMetadata metadata) {
        this.language = metadata.language();
        this.framework = metadata.framework();
        this.buildTool = metadata.buildTool();
        this.packageManager = metadata.packageManager();
        this.fileCount = metadata.fileCount();
        this.repositorySize = metadata.expandedBytes();
        this.currentVersion = this.currentVersion + 1;
        this.status = RepositoryStatus.INDEXING;
        this.failureReason = null;
        this.versions.add(new RepositoryVersion(this, currentVersion));
    }

    public void markIndexing() {
        this.status = RepositoryStatus.INDEXING;
        this.failureReason = null;
    }

    public void markReady() {
        this.status = RepositoryStatus.READY;
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        this.status = RepositoryStatus.FAILED;
        this.failureReason = failureReason;
    }
}
