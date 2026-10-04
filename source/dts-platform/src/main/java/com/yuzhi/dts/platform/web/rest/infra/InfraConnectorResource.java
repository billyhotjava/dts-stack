package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.ConnectorRegistryService;
import com.yuzhi.dts.platform.service.infra.dto.InfraConnectorDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra/connectors")
public class InfraConnectorResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final ConnectorRegistryService connectorRegistryService;
    private final AuditService auditService;

    public InfraConnectorResource(ConnectorRegistryService connectorRegistryService, AuditService auditService) {
        this.connectorRegistryService = connectorRegistryService;
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<List<InfraConnectorDto>> list(
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "includeDisabled", required = false, defaultValue = "false") boolean includeDisabled
    ) {
        List<InfraConnectorDto> connectors = connectorRegistryService.list(category, includeDisabled);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "查看连接器目录");
        meta.put("count", connectors.size());
        if (StringUtils.hasText(category)) {
            meta.put("category", category.trim());
        }
        auditService.auditAction("INFRA_CONNECTOR_REGISTRY_VIEW", AuditStage.SUCCESS, "list", meta);
        return ApiResponses.ok(connectors);
    }

    @GetMapping("/{connectorKey}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraConnectorDto> detail(@PathVariable String connectorKey) {
        InfraConnectorDto connector = connectorRegistryService.get(connectorKey);
        auditService.auditAction(
            "INFRA_CONNECTOR_REGISTRY_VIEW",
            AuditStage.SUCCESS,
            connector.connectorKey(),
            Map.of("summary", "查看连接器详情", "connectorKey", connector.connectorKey())
        );
        return ApiResponses.ok(connector);
    }

    @PostMapping("/seed")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<List<InfraConnectorDto>> seed() {
        connectorRegistryService.seedBuiltInConnectors();
        List<InfraConnectorDto> connectors = connectorRegistryService.list(null, false);
        auditService.auditAction(
            "INFRA_CONNECTOR_REGISTRY_SEED",
            AuditStage.SUCCESS,
            "built-in",
            Map.of("summary", "刷新内置连接器目录", "count", connectors.size())
        );
        return ApiResponses.ok(connectors);
    }
}
