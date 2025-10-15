package com.yuzhi.dts.platform.service.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
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
import org.springframework.web.client.RestTemplate;

/**
 * Minimal client to fetch organization tree from dts-admin for the platform.
 */
@Component
public class AdminDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(AdminDirectoryClient.class);

    private final RestTemplate restTemplate;
    private final DtsAdminProperties props;

    private static final ParameterizedTypeReference<ApiEnvelope<List<OrgNode>>> ORG_TREE_TYPE =
        new ParameterizedTypeReference<>() {};

    public AdminDirectoryClient(RestTemplateBuilder builder, DtsAdminProperties props) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.props = props;
    }

    public List<OrgNode> fetchOrgTree() {
        if (!props.isEnabled()) {
            log.debug("Admin directory client disabled via configuration");
            return List.of();
        }
        try {
            URI uri = buildUri(props.getAdminApiPath(), "/platform/orgs");
            ResponseEntity<ApiEnvelope<List<OrgNode>>> response = restExchange(uri, ORG_TREE_TYPE);
            ApiEnvelope<List<OrgNode>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                List<OrgNode> data = body.data();
                if (data != null && !data.isEmpty()) {
                    return data;
                }
                // Fallback: try sync endpoint to ensure default root exists, then refetch
                try {
                    URI syncUri = buildUri(props.getAdminApiPath(), "/platform/orgs/sync");
                    ResponseEntity<ApiEnvelope<List<OrgNode>>> syncResp = restExchange(syncUri, ORG_TREE_TYPE, HttpMethod.POST);
                    ApiEnvelope<List<OrgNode>> syncBody = syncResp.getBody();
                    if (syncBody != null && syncBody.isSuccess() && syncBody.data() != null) {
                        return syncBody.data();
                    }
                } catch (Exception syncEx) {
                    log.warn("Org sync endpoint failed: {}", syncEx.getMessage());
                    log.debug("Admin directory sync stack", syncEx);
                }
            }
            log.warn(
                "Org tree request returned no data: status={} message={} uri={}",
                body != null ? body.status() : null,
                body != null ? body.message() : null,
                uri
            );
        } catch (Exception ex) {
            log.warn("Failed to fetch org tree from dts-admin: {}", ex.getMessage());
            log.debug("Admin directory client stack", ex);
        }
        return Collections.emptyList();
    }

    private URI buildUri(String basePath, String suffix) {
        String baseUrl = props.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) baseUrl = "http://dts-admin:8081";
        String normalized = baseUrl.replaceAll("/+$", "");
        String path = (basePath == null ? "" : basePath).replaceAll("/+$", "");
        String tail = suffix == null ? "" : suffix;
        return URI.create(normalized + path + tail);
    }

    private <T> ResponseEntity<T> restExchange(URI uri, ParameterizedTypeReference<T> type) {
        return restExchange(uri, type, HttpMethod.GET);
    }

    private <T> ResponseEntity<T> restExchange(URI uri, ParameterizedTypeReference<T> type, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(props.getServiceToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getServiceToken());
        }
        if (StringUtils.hasText(props.getServiceName())) {
            headers.set("X-DTS-Service", props.getServiceName());
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, method, request, type);
    }

    public record ApiEnvelope<T>(@JsonProperty("status") String status, @JsonProperty("message") String message, @JsonProperty("data") T data) {
        public boolean isSuccess() {
            return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equals(status));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrgNode {
        private Long id;
        private String name;
        private Long parentId;
        private List<OrgNode> children;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public List<OrgNode> getChildren() { return children; }
        public void setChildren(List<OrgNode> children) { this.children = children; }
    }
}
