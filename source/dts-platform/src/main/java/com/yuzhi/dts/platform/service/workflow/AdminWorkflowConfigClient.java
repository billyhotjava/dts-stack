package com.yuzhi.dts.platform.service.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayEnvelope;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AdminWorkflowConfigClient {

    private static final Logger LOG = LoggerFactory.getLogger(AdminWorkflowConfigClient.class);

    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<WorkflowTemplateDto>>> TEMPLATE_LIST_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    private final AdminGatewayTransport transport;
    private final PlatformOutboundAdminProperties props;

    public AdminWorkflowConfigClient(AdminGatewayTransport transport, PlatformOutboundAdminProperties props) {
        this.transport = transport;
        this.props = props;
    }

    public List<WorkflowTemplateDto> listEnabledTemplates(String workflowType) {
        if (!props.isEnabled()) {
            return List.of();
        }
        String type = workflowType == null ? "" : workflowType.trim();
        if (type.isEmpty()) {
            return List.of();
        }
        try {
            return transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                org.springframework.http.HttpMethod.GET,
                buildSuffix("/platform/workflows/templates", Map.of("type", type)),
                null,
                TEMPLATE_LIST_ENVELOPE,
                AdminGatewayRequestOptions.defaults()
            );
        } catch (AdminGatewayException ex) {
            LOG.debug("Admin workflow config endpoint failed: {}", ex.getMessage());
        } catch (Exception ex) {
            LOG.debug("Admin workflow config endpoint failed: {}", ex.getMessage());
        }
        return List.of();
    }

    private String buildSuffix(String suffix, Map<String, ?> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(suffix);
        if (params != null && !params.isEmpty()) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUriString();
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowTemplateDto(
        UUID id,
        String workflowType,
        String name,
        Boolean enabled,
        Integer priority,
        String ownerScope,
        String classificationMin,
        String classificationMax,
        List<WorkflowStepDto> steps
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowStepDto(Integer stepOrder, String approverRole, Boolean deptBinding) {}
}
