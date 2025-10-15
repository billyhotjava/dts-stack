package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSecureView;
import com.yuzhi.dts.platform.repository.catalog.CatalogSecureViewRepository;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Resolve the appropriate secure view for the current user and dataset.
 */
@Component
public class SecureViewResolver {

    private final CatalogSecureViewRepository secureViewRepository;
    private final AccessChecker accessChecker;

    public SecureViewResolver(CatalogSecureViewRepository secureViewRepository, AccessChecker accessChecker) {
        this.secureViewRepository = secureViewRepository;
        this.accessChecker = accessChecker;
    }

    public Optional<String> resolve(CatalogDataset dataset) {
        if (dataset == null) {
            return Optional.empty();
        }
        List<CatalogSecureView> views = secureViewRepository.findByDataset(dataset);
        if (CollectionUtils.isEmpty(views)) {
            return Optional.empty();
        }
        Map<String, CatalogSecureView> viewByLevel = views
            .stream()
            .filter(view -> StringUtils.hasText(view.getLevel()) && StringUtils.hasText(view.getViewName()))
            .collect(Collectors.toMap(
                view -> view.getLevel().trim().toUpperCase(Locale.ROOT),
                view -> view,
                (existing, replacement) -> existing
            ));
        if (viewByLevel.isEmpty()) {
            return Optional.empty();
        }

        List<DataLevel> allowedLevels = accessChecker
            .resolveAllowedDataLevels()
            .stream()
            .sorted(Comparator.comparingInt(DataLevel::rank).reversed())
            .toList();
        for (DataLevel level : allowedLevels) {
            String key = levelKey(level);
            CatalogSecureView candidate = viewByLevel.get(key);
            if (candidate != null) {
                return Optional.of(qualify(dataset.getHiveDatabase(), candidate.getViewName()));
            }
        }
        // Fallback to any available view (e.g., PUBLIC) to avoid breaking preview when level mapping missing
        CatalogSecureView first = viewByLevel.values().stream().findFirst().orElse(null);
        if (first != null) {
            return Optional.of(qualify(dataset.getHiveDatabase(), first.getViewName()));
        }
        return Optional.empty();
    }

    private String levelKey(DataLevel level) {
        String name = level.name();
        return name.startsWith("DATA_") ? name.substring("DATA_".length()) : name;
    }

    private String qualify(String database, String viewName) {
        if (!StringUtils.hasText(viewName)) {
            return viewName;
        }
        String qualifiedName = quote(viewName.trim());
        if (StringUtils.hasText(database)) {
            return quote(database.trim()) + "." + qualifiedName;
        }
        return qualifiedName;
    }

    private String quote(String identifier) {
        String safe = identifier.replace("`", "``");
        return "`" + safe + "`";
    }
}
