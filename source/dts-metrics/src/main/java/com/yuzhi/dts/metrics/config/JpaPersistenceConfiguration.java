package com.yuzhi.dts.metrics.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code @CreatedBy}/{@code @LastModifiedBy} on
 * {@link com.yuzhi.dts.metrics.domain.AbstractAuditingEntity} are populated.
 *
 * <p>Sibling services rely on JHipster's {@code DatabaseConfiguration} for this; dts-metrics has no such
 * class, so this minimal config supplies {@code @EnableJpaAuditing} plus an {@link AuditorAware} that
 * defaults to {@code "system"} (dts-metrics has no security context wiring). The entity
 * {@code @PrePersist} fallback provides the same default, so audit columns are never null even if the
 * auditor lookup is bypassed.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "metricsAuditorAware")
public class JpaPersistenceConfiguration {

    static final String SYSTEM_PRINCIPAL = "system";

    @Bean
    AuditorAware<String> metricsAuditorAware() {
        return () -> Optional.of(SYSTEM_PRINCIPAL);
    }
}
