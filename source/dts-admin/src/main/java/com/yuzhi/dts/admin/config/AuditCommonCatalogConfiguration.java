package com.yuzhi.dts.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class AuditCommonCatalogConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditActionCatalog.class)
    public AuditActionCatalog auditActionCatalog(
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        @Value("${dts.audit.catalog-path:classpath:/config/audit-action-catalog.json}") String catalogLocation,
        @Value("${auditing.dictionary.verify-signatures:false}") boolean verifySignatures,
        @Value("${auditing.dictionary.hmac-key:}") String hmacKey,
        @Value("${auditing.dictionary.legacy-keys:}") String legacyKeysCsv,
        @Value("${auditing.dictionary.signature-suffix:.sig}") String signatureSuffix
    ) {
        return new AuditActionCatalog(
            objectMapper,
            resourceLoader,
            catalogLocation,
            verifySignatures,
            hmacKey,
            legacyKeysCsv,
            signatureSuffix
        );
    }
}
