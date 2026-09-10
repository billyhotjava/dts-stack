package com.yuzhi.dts.analytics.service.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** No plan cache: permissions and immutable versions are resolved on every BI request. */
@Component
public class PlatformIndicatorPlanClient {
    private final RestTemplate http;
    private final AnalyticsOutboundPlatformProperties properties;
    private final AnalyticsUserRepository users;
    private final ObjectMapper mapper;
    public PlatformIndicatorPlanClient(RestTemplateBuilder builder, AnalyticsOutboundPlatformProperties properties,
                                      AnalyticsUserRepository users, ObjectMapper mapper) {
        this.properties = properties; this.users = users; this.mapper = mapper;
        this.http = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(30)).build();
    }
    public record Plan(String tenantId, UUID datasourceId, String sql, List<Object> bindings, List<String> columns,
                       List<Map<String, Object>> resolvedVersions, int limit) {}
    public Plan plan(JsonNode query, PlatformContext context, Long userId) {
        if (userId == null || context == null || context.classification() == null) throw new IllegalArgumentException("指标分析需要已登录用户上下文");
        var user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("分析用户不存在"));
        if (!user.isActive() || user.getPlatformUsername() == null) throw new IllegalArgumentException("分析用户尚未关联平台身份");
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("username", user.getPlatformUsername()); actor.put("roles", context.rolesList());
        actor.put("dept", context.dept()); actor.put("classification", context.classification());
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-DTS-Service", properties.getServiceName()); headers.set("X-DTS-Service-Token", properties.getServiceToken());
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank() || properties.getServiceToken() == null || properties.getServiceToken().isBlank()) throw new IllegalStateException("平台指标服务未配置");
        var response = http.postForObject(base.replaceAll("/+$", "") + "/api/internal/indicators/plan", new HttpEntity<>(Map.of("query", query, "actor", actor), headers), JsonNode.class);
        if (response == null || response.path("data").isMissingNode()) throw new IllegalStateException("平台指标计划不可用");
        return mapper.convertValue(response.get("data"), Plan.class);
    }
}
