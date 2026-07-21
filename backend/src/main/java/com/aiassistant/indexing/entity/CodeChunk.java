package com.aiassistant.indexing.entity;

import com.aiassistant.indexing.ChunkType;
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

@Entity
@Table(name = "code_chunks")
public class CodeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SourceFile sourceFile;
    @Column(nullable = false)
    private Integer chunkIndex;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkType chunkType;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(nullable = false)
    private Integer startLine;
    @Column(nullable = false)
    private Integer endLine;
    @Column(nullable = false)
    private Long workspaceId;
    @Column(nullable = false)
    private Long repositoryId;
    @Column(nullable = false)
    private Integer version;
    @Column(nullable = false)
    private String packageName;
    @Column(nullable = false)
    private String className;
    @Column(nullable = false)
    private String methodName;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private String framework;
    @Column(nullable = false, length = 2000)
    private String filePath;

    protected CodeChunk() {
    }

    public CodeChunk(SourceFile sourceFile, Integer chunkIndex, ChunkType chunkType, String content, Integer startLine, Integer endLine,
                     Long workspaceId, Long repositoryId, Integer version, String packageName, String className, String methodName,
                     String language, String framework, String filePath) {
        this.sourceFile = sourceFile;
        this.chunkIndex = chunkIndex;
        this.chunkType = chunkType;
        this.content = content;
        this.startLine = startLine;
        this.endLine = endLine;
        this.workspaceId = workspaceId;
        this.repositoryId = repositoryId;
        this.version = version;
        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
        this.language = language;
        this.framework = framework;
        this.filePath = filePath;
    }

    public Long getId() { return id; }
    public SourceFile getSourceFile() { return sourceFile; }
    public Integer getChunkIndex() { return chunkIndex; }
    public ChunkType getChunkType() { return chunkType; }
    public String getContent() { return content; }
    public Integer getStartLine() { return startLine; }
    public Integer getEndLine() { return endLine; }
    public Long getWorkspaceId() { return workspaceId; }
    public Long getRepositoryId() { return repositoryId; }
    public Integer getVersion() { return version; }
    public String getPackageName() { return packageName; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getLanguage() { return language; }
    public String getFramework() { return framework; }
    public String getFilePath() { return filePath; }
}
