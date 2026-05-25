package com.yuzhi.dts.admin.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the inbound audit ingest endpoint (/api/audit-events).
 * <p>
 * The endpoint is reached by sibling services (dts-platform, dts-analytics, dts-ingestion)
 * over an internal network. It MUST not be reachable as anonymous in production:
 * a shared service token (or a small whitelist of tokens) is validated on every request.
 */
@ConfigurationProperties(prefix = "auditing.ingest")
public class AuditIngestProperties {

    /**
     * Whether incoming audit events must carry a recognized service token.
     * Default {@code true}. When set to false the endpoint accepts unauthenticated
     * traffic (NOT recommended outside of local development).
     */
    private boolean requireToken = true;

    /**
     * Comma-separated list of accepted bearer tokens. The token sent by the caller
     * must match one of these values to be accepted. When the list is empty AND
     * {@link #requireToken} is true, the endpoint falls back to "warn but accept"
     * to avoid breaking pre-migration deployments — operators must populate the
     * list and rotate tokens before relying on this guard.
     */
    private List<String> serviceTokens = new ArrayList<>();

    /**
     * Optional whitelist of {@code sourceSystem} values accepted by the endpoint.
     * Empty means "accept any". Useful to reject ingestion from unexpected origins.
     */
    private Set<String> allowedSourceSystems = Set.of();

    /**
     * CIDR ranges treated as container/proxy hops when resolving the real client IP of a
     * pushed audit event. The ingest endpoint is reached service-to-service, so the inbound
     * {@code Forwarded}/{@code X-Forwarded-For}/{@code remoteAddr} are usually the internal
     * bridge network; resolution must skip these in favour of the real client IP carried in
     * the body. Loopback is always treated as a container hop in addition to this list.
     * <p>
     * Default {@code 172.16.0.0/12} (Docker's default bridge range). Sites whose container
     * network uses a non-standard range (e.g. {@code 172.168.0.0/16}) must add it here via
     * {@code AUDIT_INGEST_CONTAINER_CIDRS}, otherwise that range is mistaken for a real client.
     */
    private List<String> containerCidrs = new ArrayList<>(List.of("172.16.0.0/12"));

    public boolean isRequireToken() {
        return requireToken;
    }

    public void setRequireToken(boolean requireToken) {
        this.requireToken = requireToken;
    }

    public List<String> getServiceTokens() {
        return serviceTokens;
    }

    public void setServiceTokens(List<String> serviceTokens) {
        if (serviceTokens == null) {
            this.serviceTokens = new ArrayList<>();
            return;
        }
        this.serviceTokens = serviceTokens
            .stream()
            .filter(token -> token != null && !token.isBlank())
            .map(String::trim)
            .collect(Collectors.toList());
    }

    public Set<String> getAllowedSourceSystems() {
        return allowedSourceSystems;
    }

    public void setAllowedSourceSystems(Set<String> allowedSourceSystems) {
        if (allowedSourceSystems == null || allowedSourceSystems.isEmpty()) {
            this.allowedSourceSystems = Set.of();
            return;
        }
        this.allowedSourceSystems = allowedSourceSystems
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public List<String> getContainerCidrs() {
        return containerCidrs;
    }

    public void setContainerCidrs(List<String> containerCidrs) {
        if (containerCidrs == null) {
            this.containerCidrs = new ArrayList<>();
            return;
        }
        this.containerCidrs = containerCidrs
            .stream()
            .filter(cidr -> cidr != null && !cidr.isBlank())
            .map(String::trim)
            .collect(Collectors.toList());
    }

    public boolean hasConfiguredTokens() {
        return !serviceTokens.isEmpty();
    }

    public boolean isSourceSystemAllowed(String sourceSystem) {
        if (allowedSourceSystems.isEmpty()) {
            return true;
        }
        if (sourceSystem == null) {
            return false;
        }
        return allowedSourceSystems.contains(sourceSystem.trim().toLowerCase(Locale.ROOT));
    }

    public List<String> getServiceTokensView() {
        return Collections.unmodifiableList(serviceTokens);
    }
}
