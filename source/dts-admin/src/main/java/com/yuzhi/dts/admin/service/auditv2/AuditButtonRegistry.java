package com.yuzhi.dts.admin.service.auditv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Resolves audit button metadata loaded from a JSON catalog
 * ({@code classpath:config/audit-button-registry.json} by default).
 * <p>
 * Externalising the catalog lets operations roll out new buttons or correct
 * mistakes without rebuilding the JAR. The bean fails fast if the file is
 * missing — every audit ingest call goes through this registry, so a silent
 * empty-map fallback would mask configuration drift in production.
 */
@Component
public class AuditButtonRegistry {

    private static final Logger log = LoggerFactory.getLogger(AuditButtonRegistry.class);
    private static final String DEFAULT_LOCATION = "classpath:config/audit-button-registry.json";

    private final Map<String, AuditButtonMetadata> registry;

    public AuditButtonRegistry(
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        AuditDictionarySignatureGuard signatureGuard,
        @Value("${auditing.buttons.config-location:" + DEFAULT_LOCATION + "}") String configLocation
    ) {
        this.registry = loadRegistry(resourceLoader, objectMapper, signatureGuard, configLocation);
        log.info("Loaded {} audit button metadata entries from {}", registry.size(), configLocation);
    }

    public Optional<AuditButtonMetadata> resolve(String buttonCode) {
        if (buttonCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(buttonCode.trim().toUpperCase(Locale.ROOT)));
    }

    public Map<String, AuditButtonMetadata> all() {
        return registry;
    }

    private static Map<String, AuditButtonMetadata> loadRegistry(
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        AuditDictionarySignatureGuard signatureGuard,
        String location
    ) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("audit-button-registry config not found: " + location);
        }
        byte[] content;
        try (InputStream in = resource.getInputStream()) {
            content = in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load audit-button-registry from " + location, ex);
        }
        signatureGuard.verify(location, content);
        try (InputStream in = new java.io.ByteArrayInputStream(content)) {
            CatalogFile file = objectMapper.readValue(in, CatalogFile.class);
            List<CatalogEntry> entries = file.entries();
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("audit-button-registry catalog is empty: " + location);
            }
            Map<String, AuditButtonMetadata> map = new LinkedHashMap<>();
            for (CatalogEntry entry : entries) {
                AuditButtonMetadata metadata = entry.toMetadata();
                AuditButtonMetadata previous = map.put(metadata.buttonCode(), metadata);
                if (previous != null) {
                    log.warn(
                        "Duplicate buttonCode {} in audit-button-registry; later entry overrides former",
                        metadata.buttonCode()
                    );
                }
            }
            return Collections.unmodifiableMap(map);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load audit-button-registry from " + location, ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogFile(String version, String description, List<CatalogEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogEntry(
        String buttonCode,
        String moduleKey,
        String moduleName,
        String operationCode,
        String operationName,
        String operationKind,
        boolean allowEmptyTargets
    ) {
        AuditButtonMetadata toMetadata() {
            AuditOperationKind kind;
            try {
                kind = AuditOperationKind.valueOf(operationKind);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                    "Unknown operationKind '" + operationKind + "' for buttonCode " + buttonCode,
                    ex
                );
            }
            return new AuditButtonMetadata(
                buttonCode,
                moduleKey,
                moduleName,
                operationCode,
                operationName,
                kind,
                allowEmptyTargets
            );
        }
    }
}
