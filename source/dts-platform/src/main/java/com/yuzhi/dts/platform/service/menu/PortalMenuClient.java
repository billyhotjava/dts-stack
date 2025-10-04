package com.yuzhi.dts.platform.service.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

@Component
public class PortalMenuClient {

    private static final Logger log = LoggerFactory.getLogger(PortalMenuClient.class);

    private final RestTemplate restTemplate;
    private final DtsAdminProperties props;

    private static final ParameterizedTypeReference<ApiEnvelope<List<RemoteMenuNode>>> MENU_TREE_TYPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<java.util.Map<String, Object>>> MAP_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    public PortalMenuClient(RestTemplateBuilder builder, DtsAdminProperties props) {
        this.restTemplate = builder.setConnectTimeout(java.time.Duration.ofSeconds(3)).setReadTimeout(java.time.Duration.ofSeconds(5)).build();
        this.props = props;
    }

    public List<RemoteMenuNode> fetchMenuTree() {
        if (!props.isEnabled()) {
            log.debug("Portal menu client disabled via configuration");
            return List.of();
        }
        try {
            URI uri = buildUri(props.getApiPath(), "/menu");
            ResponseEntity<ApiEnvelope<List<RemoteMenuNode>>> response = restExchange(uri, MENU_TREE_TYPE);
            ApiEnvelope<List<RemoteMenuNode>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                return body.data();
            }
            log.warn("Portal menu tree request returned no data: status={} message={} uri={}", body != null ? body.status() : null, body != null ? body.message() : null, uri);
        } catch (Exception ex) {
            log.warn("Failed to fetch portal menu tree from dts-admin: {}", ex.getMessage());
            log.debug("Portal menu tree fetch stack", ex);
        }
        return Collections.emptyList();
    }

    /**
     * Fetch portal menu tree with audience hints so that dts-admin can filter by roles/permissions/dataLevel.
     */
    public List<RemoteMenuNode> fetchMenuTreeForAudience(List<String> roles, List<String> permissions, String dataLevel) {
        if (!props.isEnabled()) {
            log.debug("Portal menu client disabled via configuration");
            return List.of();
        }
        try {
            StringBuilder qs = new StringBuilder();
            if (roles != null) {
                for (String r : roles) {
                    if (r == null || r.isBlank()) continue;
                    if (qs.length() == 0) qs.append("?"); else qs.append("&");
                    qs.append("roles=").append(urlEncode(r));
                }
            }
            if (permissions != null) {
                for (String p : permissions) {
                    if (p == null || p.isBlank()) continue;
                    if (qs.length() == 0) qs.append("?"); else qs.append("&");
                    qs.append("permissions=").append(urlEncode(p));
                }
            }
            if (dataLevel != null && !dataLevel.isBlank()) {
                if (qs.length() == 0) qs.append("?"); else qs.append("&");
                qs.append("dataLevel=").append(urlEncode(dataLevel));
            }
            URI uri = buildUri(props.getApiPath(), "/menu" + qs.toString());
            ResponseEntity<ApiEnvelope<List<RemoteMenuNode>>> response = restExchange(uri, MENU_TREE_TYPE);
            ApiEnvelope<List<RemoteMenuNode>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                return body.data();
            }
            log.warn(
                "Portal menu audience request returned no data: status={} message={} uri={}",
                body != null ? body.status() : null,
                body != null ? body.message() : null,
                uri
            );
        } catch (Exception ex) {
            log.warn("Failed to fetch portal menu tree (audience) from dts-admin: {}", ex.getMessage());
            log.debug("Portal menu audience fetch stack", ex);
        }
        return Collections.emptyList();
    }

    private URI buildUri(String basePath, String suffix) {
        String baseUrl = props.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "http://dts-admin:8081";
        }
        String normalized = baseUrl.replaceAll("/+$", "");
        String path = (basePath == null ? "" : basePath).replaceAll("/+$", "");
        String tail = suffix == null ? "" : suffix;
        return URI.create(normalized + path + tail);
    }

    private <T> ResponseEntity<T> restExchange(URI uri, ParameterizedTypeReference<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(props.getServiceToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getServiceToken());
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, request, type);
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return s;
        }
    }

    public Map<String, Object> createPortalMenu(Map<String, Object> payload) {
        return adminExchange("/portal/menus", HttpMethod.POST, payload);
    }

    public Map<String, Object> updatePortalMenu(Long id, Map<String, Object> payload) {
        return adminExchange("/portal/menus/" + id, HttpMethod.PUT, payload);
    }

    public Map<String, Object> deletePortalMenu(Long id) {
        return adminExchange("/portal/menus/" + id, HttpMethod.DELETE, null);
    }

    private Map<String, Object> adminExchange(String suffix, HttpMethod method, Map<String, Object> payload) {
        if (!props.isEnabled()) {
            log.debug("Portal menu admin exchange skipped; client disabled ({} {})", method, suffix);
            return Map.of("status", "SKIPPED");
        }
        try {
            URI uri = buildUri(props.getAdminApiPath(), suffix);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(props.getServiceToken())) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getServiceToken());
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<ApiEnvelope<Map<String, Object>>> response = restTemplate.exchange(uri, method, entity, MAP_ENVELOPE);
            ApiEnvelope<Map<String, Object>> body = response.getBody();
            if (body != null && body.isSuccess()) {
                return body.data() != null ? body.data() : Map.of("status", body.status());
            }
            log.warn(
                "Portal menu admin exchange returned failure status: status={} message={} uri={} method={} payload={}",
                body != null ? body.status() : null,
                body != null ? body.message() : null,
                uri,
                method,
                payload
            );
        } catch (Exception ex) {
            log.warn("Portal menu admin exchange failed ({} {}): {}", method, suffix, ex.getMessage());
            log.debug("Portal menu admin exchange stack", ex);
        }
        return Map.of("status", "ERROR");
    }

    public record ApiEnvelope<T>(@JsonProperty("status") String status, @JsonProperty("message") String message, @JsonProperty("data") T data) {
        public boolean isSuccess() {
            return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equals(status));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RemoteMenuNode {
        private String id;
        private String parentId;
        private String name;
        private String code;
        private Integer order;
        private Integer type;
        private String path;
        private String component;
        private String icon;
        private String metadata;
        private List<RemoteMenuNode> children;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public Integer getOrder() {
            return order;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }

        public Integer getType() {
            return type;
        }

        public void setType(Integer type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }

        public List<RemoteMenuNode> getChildren() {
            return children;
        }

        public void setChildren(List<RemoteMenuNode> children) {
            this.children = children;
        }
    }
}
