package com.aiassistant.repository.controller;

import com.aiassistant.common.SecurityUtils;
import com.aiassistant.repository.dto.RepositoryDtos.*;
import com.aiassistant.repository.service.RepositoryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class RepositoryController {
    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping("/workspaces/{workspaceId}/repositories")
    RepositoryResponse upload(@PathVariable("workspaceId") Long workspaceId, @RequestParam("file") MultipartFile file) {
        return repositoryService.upload(SecurityUtils.currentUser().id(), workspaceId, file);
    }

    @GetMapping("/repositories/{id}")
    RepositoryResponse get(@PathVariable("id") Long id) {
        return repositoryService.get(SecurityUtils.currentUser().id(), id);
    }

    @GetMapping("/repositories/{id}/status")
    RepositoryStatusResponse status(@PathVariable("id") Long id) {
        return repositoryService.status(SecurityUtils.currentUser().id(), id);
    }

    @DeleteMapping("/repositories/{id}")
    void delete(@PathVariable("id") Long id) {
        repositoryService.delete(SecurityUtils.currentUser().id(), id);
    }
}
