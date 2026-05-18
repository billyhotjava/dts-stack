package com.yuzhi.dts.opmanager.web.rest;

import com.yuzhi.dts.opmanager.runtime.DockerContainersResponse;
import com.yuzhi.dts.opmanager.runtime.DockerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opmanager/containers")
public class ContainerResource {

    private final DockerService dockerService;

    public ContainerResource(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @GetMapping
    public DockerContainersResponse listContainers() {
        return dockerService.listContainers();
    }
}
