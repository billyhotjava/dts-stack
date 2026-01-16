package com.yuzhi.dts.platform.service.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AdminWorkflowConfigClient {

    private static final Logger LOG = LoggerFactory.getLogger(AdminWorkflowConfigClient.class);

    private static final ParameterizedTypeReference<ApiEnvelope<List<WorkflowTemplateDto>>> TEMPLATE_LIST_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final DtsAdminProperties props;

    public AdminWorkflowConfigClient(RestTemplateBuilder builder, DtsAdminProperties props) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
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
        URI uri = buildUri(props.getApiPath(), "/platform/workflows/templates", Map.of("type", type));
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<ApiEnvelope<List<WorkflowTemplateDto>>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                TEMPLATE_LIST_ENVELOPE
            );
            ApiEnvelope<List<WorkflowTemplateDto>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                return body.data();
            }
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "Admin workflow config endpoint returned status {} body={} uri={}",
                ex.getStatusCode().value(),
                trim(ex.getResponseBodyAsString(), 256),
                uri
            );
        } catch (Exception ex) {
            LOG.debug("Admin workflow config endpoint failed: {}", ex.getMessage());
            LOG.trace("Admin workflow config stack", ex);
        }
        return List.of();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(props.getServiceToken())) {
            String raw = props.getServiceToken().trim();
            headers.set(HttpHeaders.AUTHORIZATION, raw.startsWith("Bearer ") ? raw : "Bearer " + raw);
        }
        return headers;
    }

    private URI buildUri(String apiPath, String suffix, Map<String, ?> params) {
        String base = props.getBaseUrl();
        String path = apiPath == null ? "/api" : apiPath;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path(path).path(suffix);
        if (params != null && !params.isEmpty()) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiEnvelope<T>(@JsonProperty("status") String status, @JsonProperty("message") String message, @JsonProperty("data") T data) {
        public boolean isSuccess() {
            return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equals(status));
        }
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

