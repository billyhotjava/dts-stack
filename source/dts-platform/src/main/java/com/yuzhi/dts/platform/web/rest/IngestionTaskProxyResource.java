package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskProxyResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final IngestionServiceClient ingestionClient;

    public IngestionTaskProxyResource(IngestionServiceClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        return ingestionClient.createIngestionTask(payload);
    }
}
