package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderDescriptor;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderRegistry;
import com.yuzhi.dts.ingestion.service.etl.api.ApiConnectorTypes;
import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpEngine;
import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpException;
import com.yuzhi.dts.ingestion.service.etl.api.ApiSourceConfigNormalizer;
import com.yuzhi.dts.ingestion.service.etl.api.ApiSourceContracts;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/ingestion/api")
public class ApiConnectorContractResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final long CONNECTION_TEST_TIMEOUT_MILLIS = 5_000L;

    private final ApiAuthProviderRegistry authProviderRegistry;
    private final com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver;
    private final ApiHttpEngine apiHttpEngine;

    public ApiConnectorContractResource(
        ApiAuthProviderRegistry authProviderRegistry,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver,
        ApiHttpEngine apiHttpEngine
    ) {
        this.authProviderRegistry = authProviderRegistry;
        this.sourceResolver = sourceResolver;
        this.apiHttpEngine = apiHttpEngine;
    }

    public record ApiConnectionTestRequest(
        UUID dataSourceId,
        Map<String, Object> resource,
        Map<String, Object> requestPolicy,
        Map<String, Object> sourceConfig,
        Map<String, Object> secrets
    ) {
        public ApiConnectionTestRequest(UUID dataSourceId, Map<String, Object> resource, Map<String, Object> requestPolicy) {
            this(dataSourceId, resource, requestPolicy, null, null);
        }
    }

    @GetMapping("/contract")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<Map<String, Object>> getContract() {
        return ResponseEntity.ok(
            Map.of(
                "contractVersion",
                ApiSourceContracts.CONTRACT_VERSION,
                "connectorType",
                ApiConnectorTypes.CONNECTOR_TYPE,
                "sourceTypes",
                ApiConnectorTypes.supportedSourceTypes(),
                "defaultReaderType",
                ApiConnectorTypes.DEFAULT_READER_TYPE,
                "syncModes",
                List.of("full_refresh", "incremental"),
                "odsLanding",
                ApiSourceContracts.odsLandingDescriptor(),
                "authProviders",
                authProviderRegistry.listDescriptors()
            )
        );
    }

    @GetMapping("/auth-providers")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<List<ApiAuthProviderDescriptor>> listAuthProviders() {
        return ResponseEntity.ok(authProviderRegistry.listDescriptors());
    }

    @PostMapping("/test-connection")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody ApiConnectionTestRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (request.dataSourceId() == null && safeMap(request.sourceConfig()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataSourceId 或 sourceConfig 不能为空");
        }
        Instant startedAt = Instant.now();
        try {
            Map<String, Object> sourceConfig = connectionTestSourceConfig(request);
            Map<String, Object> probeResource = probeResource(sourceConfig, request);
            ExecutionPlan plan = buildConnectionTestPlan(sourceConfig, request, probeResource);
            List<ApiHttpEngine.ApiHttpResult> results = apiHttpEngine.execute(plan);
            return ResponseEntity.ok(successResponse(results, probeResource, startedAt));
        } catch (ApiHttpException ex) {
            return ResponseEntity.ok(failureResponse(ex.getCode(), ex.getMessage(), ex.getStatusCode(), startedAt));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (RuntimeException ex) {
            return ResponseEntity.ok(failureResponse(null, ex.getMessage(), null, startedAt));
        }
    }

    private ExecutionPlan buildConnectionTestPlan(
        Map<String, Object> sourceConfig,
        ApiConnectionTestRequest request,
        Map<String, Object> probeResource
    ) {
        Map<String, Object> planSourceConfig = new LinkedHashMap<>(sourceConfig);
        planSourceConfig.put("requestPolicy", connectionTestPolicy(planSourceConfig, request));
        planSourceConfig.put("resources", List.of(probeResource));
        return new ExecutionPlan(
            "api-http",
            ApiConnectorTypes.CONNECTOR_TYPE,
            ApiSourceContracts.CONTRACT_VERSION,
            null,
            Map.of("sourceConfig", planSourceConfig),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "connection_test"),
            Map.of("engine", "api-http", "probe", "true")
        );
    }

    private Map<String, Object> connectionTestSourceConfig(ApiConnectionTestRequest request) {
        Map<String, Object> sourceConfig;
        if (request.dataSourceId() != null) {
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ApiConnectionInfo apiInfo = sourceResolver.resolveApiInfo(
                request.dataSourceId()
            );
            sourceConfig = sourceConfig(apiInfo);
        } else {
            sourceConfig = new LinkedHashMap<>(safeMap(request.sourceConfig()));
            Map<String, Object> secrets = new LinkedHashMap<>(safeMap(sourceConfig.get("secrets")));
            secrets.putAll(safeMap(request.secrets()));
            if (!secrets.isEmpty()) {
                sourceConfig.put("secrets", secrets);
            }
            Map<String, Object> auth = safeMap(sourceConfig.get("auth"));
            String authProvider = text(sourceConfig.get("authProvider"));
            if (!StringUtils.hasText(authProvider)) {
                authProvider = text(auth.get("provider"));
            }
            if (StringUtils.hasText(authProvider)) {
                sourceConfig.put("authProvider", authProvider);
            }
            if (!StringUtils.hasText(text(sourceConfig.get("baseUrl")))) {
                throw new IllegalArgumentException("sourceConfig.baseUrl 不能为空");
            }
        }
        return ApiSourceConfigNormalizer.normalize(sourceConfig, request.dataSourceId(), "connection_test");
    }

    private Map<String, Object> sourceConfig(
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ApiConnectionInfo apiInfo
    ) {
        Map<String, Object> sourceConfig = new LinkedHashMap<>(safeMap(apiInfo.props()));
        if (StringUtils.hasText(apiInfo.baseUrl())) {
            sourceConfig.put("baseUrl", apiInfo.baseUrl());
        }
        sourceConfig.put("authProvider", apiInfo.authProvider());
        if (!safeMap(apiInfo.authConfig()).isEmpty()) {
            sourceConfig.put("auth", safeMap(apiInfo.authConfig()));
        }
        if (!safeMap(apiInfo.defaultHeaders()).isEmpty()) {
            sourceConfig.put("defaultHeaders", safeMap(apiInfo.defaultHeaders()));
        }
        if (!safeMap(apiInfo.secrets()).isEmpty()) {
            sourceConfig.put("secrets", safeMap(apiInfo.secrets()));
        }
        return sourceConfig;
    }

    private Map<String, Object> probeResource(Map<String, Object> sourceConfig, ApiConnectionTestRequest request) {
        Map<String, Object> resource = new LinkedHashMap<>(safeMap(request.resource()));
        if (resource.isEmpty()) {
            resource.putAll(firstConfiguredResource(sourceConfig));
        }
        if (!StringUtils.hasText(text(resource.get("path")))) {
            String path = text(sourceConfig.get("path"));
            resource.put("path", StringUtils.hasText(path) ? path : "/");
        }
        resource.putIfAbsent("resourceId", "connection_test");
        Map<String, Object> pagination = new LinkedHashMap<>(safeMap(resource.get("pagination")));
        pagination.put("maxPages", 1);
        resource.put("pagination", pagination);
        return resource;
    }

    private Map<String, Object> firstConfiguredResource(Map<String, Object> props) {
        Map<String, Object> resource = safeMap(props.get("resource"));
        if (!resource.isEmpty()) {
            return resource;
        }
        Object resources = props.get("resources");
        if (resources instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                resource = safeMap(item);
                if (!resource.isEmpty()) {
                    return resource;
                }
            }
        }
        return Map.of();
    }

    private Map<String, Object> connectionTestPolicy(Map<String, Object> sourceConfig, ApiConnectionTestRequest request) {
        Map<String, Object> policy = new LinkedHashMap<>(safeMap(sourceConfig.get("requestPolicy")));
        if (request != null) {
            policy.putAll(safeMap(request.requestPolicy()));
        }
        policy.putIfAbsent("connectTimeoutMillis", CONNECTION_TEST_TIMEOUT_MILLIS);
        policy.putIfAbsent("readTimeoutMillis", CONNECTION_TEST_TIMEOUT_MILLIS);
        return policy;
    }

    private Map<String, Object> successResponse(
        List<ApiHttpEngine.ApiHttpResult> results,
        Map<String, Object> probeResource,
        Instant startedAt
    ) {
        int sampleCount = results == null ? 0 : results.stream().mapToInt(result -> result.records().size()).sum();
        Integer httpStatus = results == null || results.isEmpty() ? null : results.get(0).statusCode();
        List<Object> sampleRecords = results == null
            ? List.of()
            : results.stream().flatMap(result -> result.records().stream()).limit(5).map(record -> (Object) record).toList();
        Map<String, Object> response = baseResponse(startedAt);
        response.put("connected", true);
        if (httpStatus != null) {
            response.put("httpStatus", httpStatus);
        }
        response.put("authOk", true);
        response.put("sampleCount", sampleCount);
        response.put("recordPathResolved", StringUtils.hasText(text(probeResource.get("recordPath"))));
        if (!sampleRecords.isEmpty()) {
            response.put("sampleRecords", sampleRecords);
        }
        return response;
    }

    private Map<String, Object> failureResponse(String code, String message, Integer httpStatus, Instant startedAt) {
        String marker = StringUtils.hasText(code) ? code + " " + message : message;
        String category = ExecutionFailureClassifier.classify(marker);
        Map<String, Object> response = baseResponse(startedAt);
        response.put("connected", false);
        if (httpStatus != null) {
            response.put("httpStatus", httpStatus);
        }
        response.put("authOk", !ExecutionFailureClassifier.CATEGORY_PERMISSION.equals(category));
        response.put("sampleCount", 0);
        response.put("recordPathResolved", false);
        if (StringUtils.hasText(code)) {
            response.put("errorCode", code);
        }
        if (StringUtils.hasText(message)) {
            response.put("message", message);
        }
        response.put("failureCategory", category);
        response.put("advice", ExecutionFailureClassifier.advice(category));
        return response;
    }

    private Map<String, Object> baseResponse(Instant startedAt) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("elapsedMs", Duration.between(startedAt, Instant.now()).toMillis());
        return response;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private Map<String, Object> safeMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
