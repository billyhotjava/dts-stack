package com.yuzhi.dts.platform.service.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayEnvelope;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PortalMenuClient {

    private static final Logger log = LoggerFactory.getLogger(PortalMenuClient.class);

    private final AdminGatewayTransport transport;
    private final DtsAdminProperties props;

    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<RemoteMenuNode>>> MENU_TREE_TYPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<java.util.Map<String, Object>>> MAP_ENVELOPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<PortalMenuCollection>> MENU_COLLECTION_TYPE =
        new ParameterizedTypeReference<>() {};

    public PortalMenuClient(AdminGatewayTransport transport, DtsAdminProperties props) {
        this.transport = transport;
        this.props = props;
    }

    public List<RemoteMenuNode> fetchMenuTree() {
        if (!props.isEnabled()) {
            log.debug("Portal menu client disabled via configuration");
            return List.of();
        }
        try {
            List<RemoteMenuNode> data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                org.springframework.http.HttpMethod.GET,
                "/menu",
                null,
                MENU_TREE_TYPE,
                AdminGatewayRequestOptions.defaults()
            );
            return data != null ? data : List.of();
        } catch (AdminGatewayException ex) {
            log.warn("Failed to fetch portal menu tree from dts-admin: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Fetch portal menu tree with audience hints so that dts-admin can filter by角色与权限。
     */
    public List<RemoteMenuNode> fetchMenuTreeForAudience(List<String> roles, List<String> permissions) {
        if (!props.isEnabled()) {
            log.debug("Portal menu client disabled via configuration");
            return List.of();
        }
        try {
            List<RemoteMenuNode> data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                org.springframework.http.HttpMethod.GET,
                buildAudienceSuffix(roles, permissions),
                null,
                MENU_TREE_TYPE,
                AdminGatewayRequestOptions.defaults()
            );
            return data != null ? data : List.of();
        } catch (AdminGatewayException ex) {
            log.warn("Failed to fetch portal menu tree (audience) from dts-admin: {}", ex.getMessage());
        }
        return Collections.emptyList();
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
            Map<String, Object> data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.ADMIN_API,
                method,
                suffix,
                payload,
                MAP_ENVELOPE,
                AdminGatewayRequestOptions.defaults()
            );
            return data != null ? data : Map.of("status", "SUCCESS");
        } catch (Exception ex) {
            log.warn("Portal menu admin exchange failed ({} {}): {}", method, suffix, ex.getMessage());
        }
        return Map.of("status", "ERROR");
    }

    public List<RemoteMenuNode> fetchActiveMenuTree() {
        if (!props.isEnabled()) {
            return List.of();
        }
        try {
            PortalMenuCollection data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.ADMIN_API,
                org.springframework.http.HttpMethod.GET,
                "/portal/menus",
                null,
                MENU_COLLECTION_TYPE,
                AdminGatewayRequestOptions.defaults()
            );
            if (data != null && data.getMenus() != null) {
                return data.getMenus();
            }
        } catch (Exception ex) {
            log.debug("Failed to fetch active menu tree via admin API: {}", ex.getMessage());
        }
        return List.of();
    }

    private String buildAudienceSuffix(List<String> roles, List<String> permissions) {
        StringBuilder qs = new StringBuilder("/menu");
        boolean hasQuery = false;
        if (roles != null) {
            for (String role : roles) {
                if (!StringUtils.hasText(role)) continue;
                qs.append(hasQuery ? "&" : "?");
                qs.append("roles=").append(urlEncode(role));
                hasQuery = true;
            }
        }
        if (permissions != null) {
            for (String permission : permissions) {
                if (!StringUtils.hasText(permission)) continue;
                qs.append(hasQuery ? "&" : "?");
                qs.append("permissions=").append(urlEncode(permission));
                hasQuery = true;
            }
        }
        return qs.toString();
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static class PortalMenuCollection {
        @JsonProperty("menus")
        private List<RemoteMenuNode> menus;

        @JsonProperty("allMenus")
        private List<RemoteMenuNode> allMenus;

        public List<RemoteMenuNode> getMenus() {
            return menus;
        }

        public void setMenus(List<RemoteMenuNode> menus) {
            this.menus = menus;
        }

        public List<RemoteMenuNode> getAllMenus() {
            return allMenus;
        }

        public void setAllMenus(List<RemoteMenuNode> allMenus) {
            this.allMenus = allMenus;
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
        private Boolean deleted;

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

        public Boolean getDeleted() {
            return deleted;
        }

        public void setDeleted(Boolean deleted) {
            this.deleted = deleted;
        }
    }
}
