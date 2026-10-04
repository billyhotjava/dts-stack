package com.yuzhi.dts.admin.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the inbound audit ingest endpoint (/api/audit-events).
 * <p>
 * Network normalization settings for the inbound audit endpoint. Pairwise sibling-service
 * authentication is owned by {@link AdminInboundServiceAuthProperties}.
 */
@ConfigurationProperties(prefix = "auditing.ingest")
public class AuditIngestProperties {

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
    private int maxBodyBytes = 262_144;
    private int rateLimitPerSecond = 100;
    private int rateLimitBurst = 200;

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

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = Math.max(1, Math.min(maxBodyBytes, 1_048_576));
    }

    public int getRateLimitPerSecond() {
        return rateLimitPerSecond;
    }

    public void setRateLimitPerSecond(int rateLimitPerSecond) {
        this.rateLimitPerSecond = Math.max(1, Math.min(rateLimitPerSecond, 10_000));
    }

    public int getRateLimitBurst() {
        return rateLimitBurst;
    }

    public void setRateLimitBurst(int rateLimitBurst) {
        this.rateLimitBurst = Math.max(1, Math.min(rateLimitBurst, 20_000));
    }

}
