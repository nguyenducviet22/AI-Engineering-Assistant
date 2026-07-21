package com.aiassistant.indexing.entity;

import com.aiassistant.repository.entity.RepositoryVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "source_files")
public class SourceFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private RepositoryVersion repositoryVersion;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false, length = 2000)
    private String path;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private Long sizeBytes;
    @Column(nullable = false, length = 64)
    private String checksum;

    protected SourceFile() {
    }

    public SourceFile(RepositoryVersion repositoryVersion, String fileName, String path, String language, Long sizeBytes, String checksum) {
        this.repositoryVersion = repositoryVersion;
        this.fileName = fileName;
        this.path = path;
        this.language = language;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
    }

    public Long getId() { return id; }
    public RepositoryVersion getRepositoryVersion() { return repositoryVersion; }
    public String getFileName() { return fileName; }
    public String getPath() { return path; }
    public String getLanguage() { return language; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksum() { return checksum; }
}
