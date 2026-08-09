package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelWorkbenchCatalogRepository;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogPage;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogQuery;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies authorization and bounded-window rules to the model workbench projection. */
@Service
public class ModelWorkbenchCatalogQueryService {

    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 128;
    private static final Set<String> OBJECT_TYPES = Set.of(
        "DIMENSION_DEFINITION",
        "DIMENSION",
        "FACT",
        "SUMMARY",
        "APPLICATION"
    );
    private static final Set<String> STATUSES = Set.of(
        "DRAFT",
        "CURRENT",
        "RETIRED",
        "DESIGNING",
        "VALIDATING",
        "READY_TO_PUBLISH",
        "PUBLISHED",
        "ARCHIVED"
    );

    private final ModelWorkbenchCatalogRepository repository;
    private final ModelSpecDomainReadAccessPort domainReadAccess;

    public ModelWorkbenchCatalogQueryService(
        ModelWorkbenchCatalogRepository repository,
        ModelSpecDomainReadAccessPort domainReadAccess
    ) {
        this.repository = repository;
        this.domainReadAccess = domainReadAccess;
    }

    @Transactional(readOnly = true)
    public CatalogPage query(String tenantId, CatalogQuery request) {
        CatalogQuery normalized = normalize(request);
        String effectiveTenantId = tenantId == null ? null : tenantId.trim();
        if (effectiveTenantId == null || effectiveTenantId.isEmpty()) {
            throw invalidWindow("A server tenant is required");
        }

        Set<UUID> visibleDomainIds = domainReadAccess.visibleDomainIds();
        Set<UUID> effectiveVisibleDomainIds = visibleDomainIds == null
            ? Set.of()
            : visibleDomainIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (
            effectiveVisibleDomainIds.isEmpty() ||
            (normalized.domainId() != null && !effectiveVisibleDomainIds.contains(normalized.domainId()))
        ) {
            return CatalogPage.empty(normalized.page(), normalized.size());
        }
        return repository.page(effectiveTenantId, effectiveVisibleDomainIds, normalized);
    }

    private static CatalogQuery normalize(CatalogQuery request) {
        if (request == null || request.page() < 0 || request.page() > MAX_PAGE || request.size() < 1 || request.size() > MAX_SIZE) {
            throw invalidWindow("The workbench page must be bounded to 1-100 rows");
        }
        String query = trimToNull(request.query());
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw invalidWindow("The workbench search text cannot exceed 128 characters");
        }
        String objectType = upper(request.objectType());
        if (objectType != null && !OBJECT_TYPES.contains(objectType)) {
            throw invalidWindow("The workbench object type is invalid");
        }
        String status = upper(request.status());
        if (status != null && !STATUSES.contains(status)) {
            throw invalidWindow("The workbench status is invalid");
        }
        return new CatalogQuery(
            request.page(),
            request.size(),
            query,
            request.planId(),
            request.domainId(),
            objectType,
            request.layer(),
            status
        );
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static ModelSpecException invalidWindow(String message) {
        return new ModelSpecException(
            "MODEL_WORKBENCH_PAGE_WINDOW_INVALID",
            message,
            ModelSpecException.Kind.BAD_REQUEST
        );
    }
}
