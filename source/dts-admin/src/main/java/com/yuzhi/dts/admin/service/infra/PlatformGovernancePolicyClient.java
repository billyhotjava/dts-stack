package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.config.PlatformIntegrationProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Reads and changes the platform model governance policy (F13-T09). dts-platform owns the policy; this client
 * never caches or mirrors it. Unlike {@link PlatformInfraClient} it does not swallow failures: the administrator
 * must see that a change was not applied.
 */
@Component
public class PlatformGovernancePolicyClient {

    private static final String POLICY_PATH = "/internal/modeling/governance-policy";

    private final RestTemplate restTemplate;
    private final PlatformIntegrationProperties properties;

    @Autowired
    public PlatformGovernancePolicyClient(RestTemplateBuilder builder, PlatformIntegrationProperties properties) {
        this(builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build(), properties);
    }

    PlatformGovernancePolicyClient(RestTemplate restTemplate, PlatformIntegrationProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public record PolicyView(
        String qualityGate,
        String standardCoverage,
        int revision,
        String lastModifiedBy,
        Instant lastModifiedDate,
        boolean systemDefault
    ) {}

    public record ImpactView(long unfrozenCandidates, long frozenCandidates) {}

    /** A failure the administrator must see; {@code status} is what admin returns to its own caller. */
    public static class PlatformPolicyException extends RuntimeException {

        private final HttpStatus status;
        private final String code;

        public PlatformPolicyException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public HttpStatus status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    public PolicyView current() {
        return exchange(HttpMethod.GET, POLICY_PATH, null, PolicyView.class);
    }

    public ImpactView impact() {
        return exchange(HttpMethod.GET, POLICY_PATH + "/impact", null, ImpactView.class);
    }

    public PolicyView update(String qualityGate, int expectedRevision, String reason) {
        return exchange(
            HttpMethod.PUT,
            POLICY_PATH,
            Map.of("qualityGate", qualityGate, "expectedRevision", expectedRevision, "reason", reason),
            PolicyView.class
        );
    }

    private <T> T exchange(HttpMethod method, String suffix, Object body, Class<T> type) {
        if (!properties.isEnabled()) {
            throw unavailable("PLATFORM_INTEGRATION_DISABLED", "平台联动未启用，无法读取或修改模型发布治理策略");
        }
        try {
            T response = restTemplate.exchange(buildUri(suffix), method, new HttpEntity<>(body, headers()), type).getBody();
            if (response == null) {
                throw unavailable("PLATFORM_POLICY_EMPTY_RESPONSE", "平台未返回模型发布治理策略");
            }
            return response;
        } catch (HttpStatusCodeException rejected) {
            if (rejected.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new PlatformPolicyException(
                    HttpStatus.CONFLICT,
                    "GOVERNANCE_POLICY_REVISION_CONFLICT",
                    "策略已被他人修改，请刷新后重试"
                );
            }
            if (rejected.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new PlatformPolicyException(HttpStatus.BAD_REQUEST, "GOVERNANCE_POLICY_REQUEST_INVALID", "策略取值无效");
            }
            if (rejected.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw new PlatformPolicyException(
                    HttpStatus.UNAUTHORIZED, "GOVERNANCE_POLICY_LOGIN_REQUIRED", "登录身份已失效，请重新登录后重试"
                );
            }
            if (rejected.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                throw new PlatformPolicyException(
                    HttpStatus.FORBIDDEN, "GOVERNANCE_POLICY_ACCESS_DENIED", "当前账号无权管理模型发布治理策略"
                );
            }
            throw unavailable("PLATFORM_POLICY_REQUEST_FAILED", "平台服务处理失败，策略未修改");
        } catch (ResourceAccessException unreachable) {
            throw unavailable("PLATFORM_UNREACHABLE", "平台服务不可用，策略未修改");
        }
    }

    private static PlatformPolicyException unavailable(String code, String message) {
        return new PlatformPolicyException(HttpStatus.BAD_GATEWAY, code, message);
    }

    private HttpHeaders headers() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new PlatformPolicyException(
                HttpStatus.UNAUTHORIZED, "GOVERNANCE_POLICY_LOGIN_REQUIRED", "请重新登录后管理模型发布治理策略"
            );
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Forward the authenticated person's access token, never an unverified incoming header.
        headers.setBearerAuth(jwt.getToken().getTokenValue());
        return headers;
    }

    private URI buildUri(String suffix) {
        String base = StringUtils.hasText(properties.getBaseUrl()) ? properties.getBaseUrl() : "http://dts-platform:8081";
        String path = StringUtils.hasText(properties.getApiPath()) ? properties.getApiPath() : "";
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(base.replaceAll("/+$", "") + path.replaceAll("/+$", "") + suffix);
    }
}
