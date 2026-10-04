package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetResolutionFailureRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogAssetIdentityResolutionAuditService {

    public static final String REASON_UNKNOWN_TYPE_HINT = "UNKNOWN_TYPE_HINT";
    public static final String REASON_TYPE_REPOSITORY_MISS = "TYPE_REPOSITORY_MISS";
    public static final String REASON_LEGACY_FORMAT_NOT_RECOGNIZED = "LEGACY_FORMAT_NOT_RECOGNIZED";
    public static final String REASON_AMBIGUOUS_MATCH = "AMBIGUOUS_MATCH";

    private static final Logger log = LoggerFactory.getLogger(CatalogAssetIdentityResolutionAuditService.class);
    private static final int MAX_REF_LENGTH = 2048;
    private static final int MAX_CALLER_LENGTH = 128;
    private static final int MAX_TYPE_HINT_LENGTH = 64;
    private static final int MAX_REASON_LENGTH = 64;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final CatalogAssetResolutionFailureRepository repository;

    public CatalogAssetIdentityResolutionAuditService(CatalogAssetResolutionFailureRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String ref, String caller, String reason) {
        try {
            CatalogAssetResolutionFailure failure = new CatalogAssetResolutionFailure();
            failure.setRef(limit(firstText(ref, "<blank>"), MAX_REF_LENGTH));
            failure.setRequestedAt(Instant.now());
            failure.setCaller(limit(firstText(MDC.get("catalog.resolver.caller"), MDC.get("caller"), caller, "unknown"), MAX_CALLER_LENGTH));
            failure.setTypeHintGuess(limit(typeHintGuess(ref), MAX_TYPE_HINT_LENGTH));
            failure.setReason(limit(firstText(reason, REASON_TYPE_REPOSITORY_MISS), MAX_REASON_LENGTH));
            repository.save(failure);
        } catch (RuntimeException ex) {
            log.warn("catalog asset identity resolution audit failed: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<CatalogAssetResolutionFailure> recentFailures(Instant since, int limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit), Sort.by(Sort.Direction.DESC, "requestedAt"));
        if (since == null) {
            return repository.findAllByOrderByRequestedAtDesc(page);
        }
        return repository.findByRequestedAtGreaterThanEqualOrderByRequestedAtDesc(since, page);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    static String typeHintGuess(String ref) {
        String value = StringUtils.trimWhitespace(ref);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            return normalized.substring(0, colon).replace('-', '_');
        }
        int dot = normalized.indexOf('.');
        if (dot > 0) {
            return normalized.substring(0, dot).replace('-', '_');
        }
        return null;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
