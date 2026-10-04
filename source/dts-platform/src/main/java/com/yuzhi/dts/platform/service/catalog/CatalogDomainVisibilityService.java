package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainActorProvider.CatalogDomainActor;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import jakarta.persistence.criteria.Predicate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogDomainVisibilityService {

    private static final String DOMAIN_ASSET_TYPE = CatalogAssetType.CATALOG_DOMAIN.name();
    private static final Pageable ALL_ACCESSIBLE_IDS = PageRequest.of(0, Integer.MAX_VALUE);
    private static final Sort TREE_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final CatalogDomainRepository domainRepository;
    private final AssetPermissionService permissionService;
    private final CatalogDomainActorProvider actorProvider;

    public CatalogDomainVisibilityService(
        CatalogDomainRepository domainRepository,
        AssetPermissionService permissionService,
        CatalogDomainActorProvider actorProvider
    ) {
        this.domainRepository = domainRepository;
        this.permissionService = permissionService;
        this.actorProvider = actorProvider;
    }

    public Page<CatalogDomain> findVisiblePage(String keyword, Pageable pageable) {
        return domainRepository.findAll(visibleSpecification(normalizeKeyword(keyword), resolveScope()), pageable);
    }

    public List<CatalogDomain> findAllVisible() {
        return domainRepository.findAll(visibleSpecification(null, resolveScope()), TREE_SORT);
    }

    public long countVisible() {
        return domainRepository.count(visibleSpecification(null, resolveScope()));
    }

    public DomainCodeVisibility resolveCodes(Set<String> codes) {
        Set<String> normalizedCodes = new LinkedHashSet<>();
        if (codes != null) {
            for (String code : codes) {
                String normalizedCode = normalizeCode(code);
                if (normalizedCode != null) {
                    normalizedCodes.add(normalizedCode);
                }
            }
        }
        if (normalizedCodes.isEmpty()) {
            return new DomainCodeVisibility(Map.of(), Set.of());
        }

        List<CatalogDomain> registeredDomains = domainRepository.findByCodeLowerIn(normalizedCodes);
        Set<UUID> registeredIds = registeredDomains
            .stream()
            .map(CatalogDomain::getId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> visibleIds = findVisibleIds(registeredIds);
        Map<String, String> visibleNames = new LinkedHashMap<>();
        Set<String> registeredCodes = new LinkedHashSet<>();
        for (CatalogDomain domain : registeredDomains) {
            String normalizedCode = normalizeCode(domain.getCode());
            if (normalizedCode == null) {
                continue;
            }
            registeredCodes.add(normalizedCode);
            if (domain.getId() != null && visibleIds.contains(domain.getId())) {
                String displayName = domain.getName() == null || domain.getName().isBlank()
                    ? domain.getCode()
                    : domain.getName();
                visibleNames.putIfAbsent(normalizedCode, displayName);
            }
        }
        Set<String> hiddenCodes = new LinkedHashSet<>(registeredCodes);
        hiddenCodes.removeAll(visibleNames.keySet());
        return new DomainCodeVisibility(visibleNames, hiddenCodes);
    }

    public Optional<CatalogDomain> findVisibleById(UUID domainId) {
        Specification<CatalogDomain> idSpecification = (root, query, builder) -> builder.equal(root.get("id"), domainId);
        return domainRepository.findOne(visibleSpecification(null, resolveScope()).and(idSpecification));
    }

    public Set<UUID> findVisibleIds(Set<UUID> domainIds) {
        if (domainIds == null || domainIds.isEmpty()) {
            return Set.of();
        }
        Specification<CatalogDomain> idsSpecification = (root, query, builder) -> root.get("id").in(domainIds);
        return Set.copyOf(
            domainRepository
                .findAll(visibleSpecification(null, resolveScope()).and(idsSpecification))
                .stream()
                .map(CatalogDomain::getId)
                .toList()
        );
    }

    public boolean canRead(CatalogDomain domain) {
        if (domain == null) {
            return false;
        }
        if (domain.getAccessPolicy() == CatalogDomainAccessPolicy.PUBLIC) {
            return true;
        }
        if (domain.getId() == null) {
            return false;
        }
        CatalogDomainActor actor = actorProvider.currentActor();
        if (actor == null) {
            return false;
        }
        return permissionService
            .check(actor.username(), actor.roles(), actor.departmentCode(), DOMAIN_ASSET_TYPE, domain.getId().toString())
            .allowed();
    }

    public boolean canMaintain(CatalogDomain domain) {
        if (domain == null) {
            return false;
        }
        if (domain.getAccessPolicy() == CatalogDomainAccessPolicy.PUBLIC) {
            return true;
        }
        if (domain.getId() == null) {
            return false;
        }
        CatalogDomainActor actor = actorProvider.currentActor();
        if (actor == null) {
            return false;
        }
        PermissionResult result = permissionService.check(
            actor.username(),
            actor.roles(),
            actor.departmentCode(),
            DOMAIN_ASSET_TYPE,
            domain.getId().toString()
        );
        return (
            result.allowed() && ("EDIT".equalsIgnoreCase(result.permission()) || "MANAGE".equalsIgnoreCase(result.permission()))
        );
    }

    private AccessScope resolveScope() {
        CatalogDomainActor actor = actorProvider.currentActor();
        if (actor == null) {
            return new AccessScope(false, Set.of());
        }
        AccessibleAssetsResult accessible = permissionService.listAccessibleAssetIds(
            actor.username(),
            actor.roles(),
            actor.departmentCode(),
            DOMAIN_ASSET_TYPE,
            ALL_ACCESSIBLE_IDS
        );
        boolean allRestrictedVisible = "ALL".equalsIgnoreCase(accessible.scope());
        Set<UUID> accessibleIds = new LinkedHashSet<>();
        if (!allRestrictedVisible) {
            for (String assetId : accessible.assetIds()) {
                try {
                    accessibleIds.add(UUID.fromString(assetId));
                } catch (IllegalArgumentException ignored) {
                    // Permission rows for other ID formats cannot authorize a catalog domain.
                }
            }
        }
        return new AccessScope(allRestrictedVisible, Set.copyOf(accessibleIds));
    }

    private static Specification<CatalogDomain> visibleSpecification(String keyword, AccessScope scope) {
        return (root, query, builder) -> {
            Predicate visible = builder.equal(root.get("accessPolicy"), CatalogDomainAccessPolicy.PUBLIC);
            if (scope.allRestrictedVisible()) {
                visible = builder.conjunction();
            } else if (!scope.accessibleIds().isEmpty()) {
                visible = builder.or(visible, root.get("id").in(scope.accessibleIds()));
            }
            if (keyword == null) {
                return visible;
            }
            String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            Predicate keywordMatch = builder.or(
                builder.like(builder.lower(root.get("name")), pattern),
                builder.like(builder.lower(root.get("code")), pattern),
                builder.like(builder.lower(root.get("owner")), pattern),
                builder.like(builder.lower(root.get("description")), pattern)
            );
            return builder.and(visible, keywordMatch);
        };
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toLowerCase(Locale.ROOT);
    }

    public record DomainCodeVisibility(Map<String, String> visibleNames, Set<String> hiddenCodes) {
        public DomainCodeVisibility {
            visibleNames = visibleNames == null ? Map.of() : Map.copyOf(visibleNames);
            hiddenCodes = hiddenCodes == null ? Set.of() : Set.copyOf(hiddenCodes);
        }

        public Optional<String> visibleName(String code) {
            return Optional.ofNullable(visibleNames.get(normalizeCode(code)));
        }

        public boolean isHidden(String code) {
            return hiddenCodes.contains(normalizeCode(code));
        }
    }

    private record AccessScope(boolean allRestrictedVisible, Set<UUID> accessibleIds) {}
}
