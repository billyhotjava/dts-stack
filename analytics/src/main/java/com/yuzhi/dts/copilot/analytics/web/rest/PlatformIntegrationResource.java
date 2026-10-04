package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.PlatformInfraClient;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/platform")
public class PlatformIntegrationResource {

    private final AnalyticsSessionService sessionService;
    private final PlatformInfraClient platformInfraClient;

    public PlatformIntegrationResource(AnalyticsSessionService sessionService, PlatformInfraClient platformInfraClient) {
        this.sessionService = sessionService;
        this.platformInfraClient = platformInfraClient;
    }

    @GetMapping(path = "/data-sources", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dataSources(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        List<PlatformInfraClient.DataSourceSummary> list = platformInfraClient.listDataSources();
        List<PlatformInfraClient.DataSourceSummary> filtered = list.stream()
            .filter(item -> StringUtils.hasText(item.jdbcUrl()))
            .toList();
        return ResponseEntity.ok(filtered);
    }

    @PostMapping(path = "/data-sources", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDataSource(
            @RequestBody CreateDataSourceRequest request,
            HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }
        try {
            PlatformInfraClient.DataSourceSummary created = platformInfraClient.createDataSource(new PlatformInfraClient.CreateDataSourceRequest(
                    request.name(),
                    request.type(),
                    request.jdbcUrl(),
                    request.host(),
                    request.port(),
                    request.database(),
                    request.serviceName(),
                    request.sid(),
                    request.username(),
                    request.password(),
                    request.description()));
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping(path = "/data-sources/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDataSource(
            @PathVariable("id") long id,
            HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }
        try {
            PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(id);
            return ResponseEntity.ok(detail);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping(path = "/data-sources/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateDataSource(
            @PathVariable("id") long id,
            @RequestBody CreateDataSourceRequest request,
            HttpServletRequest servletRequest) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, servletRequest);
        if (auth.isPresent()) {
            return auth.get();
        }
        try {
            PlatformInfraClient.DataSourceSummary updated = platformInfraClient.updateDataSource(id, new PlatformInfraClient.CreateDataSourceRequest(
                    request.name(),
                    request.type(),
                    request.jdbcUrl(),
                    request.host(),
                    request.port(),
                    request.database(),
                    request.serviceName(),
                    request.sid(),
                    request.username(),
                    request.password(),
                    request.description()));
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping(path = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> metrics(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/visible-tables", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> visibleTables(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/context", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> context(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        String dept = request.getHeader("X-DTS-Dept");
        String level = request.getHeader("X-DTS-Classification");
        String roles = request.getHeader("X-DTS-Roles");
        return ResponseEntity.ok(Map.of(
                "dept", dept,
                "classification", level,
                "roles", roles));
    }

    public record CreateDataSourceRequest(
            String name,
            String type,
            String jdbcUrl,
            String host,
            Integer port,
            String database,
            String serviceName,
            String sid,
            String username,
            String password,
            String description) {}
}
