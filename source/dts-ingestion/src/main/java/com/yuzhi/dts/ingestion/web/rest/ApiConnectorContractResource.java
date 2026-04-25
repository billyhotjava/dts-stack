package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderDescriptor;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderRegistry;
import com.yuzhi.dts.ingestion.service.etl.api.ApiConnectorTypes;
import com.yuzhi.dts.ingestion.service.etl.api.ApiSourceContracts;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion/api")
public class ApiConnectorContractResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final ApiAuthProviderRegistry authProviderRegistry;

    public ApiConnectorContractResource(ApiAuthProviderRegistry authProviderRegistry) {
        this.authProviderRegistry = authProviderRegistry;
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
                List.of("api", "http", "http_api", "rest_api"),
                "defaultReaderType",
                ApiConnectorTypes.DEFAULT_READER_TYPE,
                "syncModes",
                List.of("full_refresh", "incremental"),
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
}

