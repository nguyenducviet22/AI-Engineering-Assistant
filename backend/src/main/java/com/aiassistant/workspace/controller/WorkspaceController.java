package com.aiassistant.workspace.controller;

import com.aiassistant.common.SecurityUtils;
import com.aiassistant.workspace.dto.WorkspaceDtos.*;
import com.aiassistant.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    List<WorkspaceResponse> list() {
        return workspaceService.list(SecurityUtils.currentUser().id());
    }

    @PostMapping
    WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.create(SecurityUtils.currentUser().id(), request);
    }

    @GetMapping("/{id}")
    WorkspaceResponse get(@PathVariable("id") Long id) {
        return workspaceService.get(SecurityUtils.currentUser().id(), id);
    }

    @PutMapping("/{id}")
    WorkspaceResponse update(@PathVariable("id") Long id, @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.update(SecurityUtils.currentUser().id(), id, request);
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") Long id) {
        workspaceService.delete(SecurityUtils.currentUser().id(), id);
    }
}
