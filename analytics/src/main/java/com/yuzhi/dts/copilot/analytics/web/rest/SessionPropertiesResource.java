package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.web.ui.MetabaseBootstrapService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionPropertiesResource {

    private final MetabaseBootstrapService bootstrapService;

    public SessionPropertiesResource(MetabaseBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping("/properties")
    public ResponseEntity<ObjectNode> properties(HttpServletRequest request) {
        return ResponseEntity.ok(bootstrapService.buildBootstrap(request));
    }
}

