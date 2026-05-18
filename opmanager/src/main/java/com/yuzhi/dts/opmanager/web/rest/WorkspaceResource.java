package com.yuzhi.dts.opmanager.web.rest;

import com.yuzhi.dts.opmanager.workspace.WorkspaceOperationResult;
import com.yuzhi.dts.opmanager.workspace.WorkspaceService;
import com.yuzhi.dts.opmanager.workspace.WorkspaceStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opmanager/workspace")
public class WorkspaceResource {

    private final WorkspaceService workspaceService;

    public WorkspaceResource(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public WorkspaceStatus status() {
        return workspaceService.status();
    }

    @PostMapping("/load-images")
    public WorkspaceOperationResult loadImages() {
        return workspaceService.loadImages();
    }

    @PostMapping("/recreate-containers")
    public WorkspaceOperationResult recreateContainers() {
        return workspaceService.recreateContainers();
    }
}
