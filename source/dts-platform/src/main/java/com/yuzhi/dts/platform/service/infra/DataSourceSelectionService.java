package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DataSourceSelectionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String CAPABILITY_MODELING_LAKEHOUSE = "MODELING_LAKEHOUSE";
    private static final String CAPABILITY_DBT_TARGET = "DBT_TARGET";
    private static final String CAPABILITY_ANALYTICS_REGISTERABLE = "ANALYTICS_REGISTERABLE";
    private static final String CAPABILITY_QUERYABLE = "QUERYABLE";
    private static final String DEFAULT_SOURCE_ADMIN = "ADMIN_DEFAULT";
    private static final String DEFAULT_SOURCE_SINGLE = "SINGLE_AVAILABLE";
    private static final String DEFAULT_SOURCE_NONE = "NONE";
    private static final java.util.Set<String> JDBC_TYPES = java.util.Set.of(
        "jdbc",
        "postgres",
        "postgresql",
        "mysql",
        "mariadb",
        "oracle",
        "dm",
        "kingbase",
        "gbase",
        "sqlserver",
        "clickhouse",
        "hive",
        "inceptor",
        "db2"
    );

    private final InfraDataSourceRepository dataSourceRepository;
    private final DefaultDestinationSyncService defaultDestinationSyncService;

    public DataSourceSelectionService(
        InfraDataSourceRepository dataSourceRepository,
        DefaultDestinationSyncService defaultDestinationSyncService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.defaultDestinationSyncService = defaultDestinationSyncService;
    }

    public DataSourceSelectionResponse listSelections(String capability, String activeDeptHeader) {
        String normalizedCapability = normalizeCapability(capability);
        DefaultDestinationSyncService.DefaultDestinationStatus defaultStatus = resolveDefaultStatus().orElse(null);
        List<InfraDataSource> sources = loadVisibleSources(activeDeptHeader)
            .stream()
            .filter(source -> supportsCapability(source, normalizedCapability))
            .toList();
        UUID adminDefaultId = parseUuid(defaultStatus != null ? defaultStatus.dataSourceId() : null);
        boolean hasAdminDefault = adminDefaultId != null && sources.stream().anyMatch(source -> adminDefaultId.equals(source.getId()));
        UUID selectedDefaultId = hasAdminDefault ? adminDefaultId : (sources.size() == 1 ? sources.get(0).getId() : null);
        String defaultSource = hasAdminDefault ? DEFAULT_SOURCE_ADMIN : (selectedDefaultId != null ? DEFAULT_SOURCE_SINGLE : DEFAULT_SOURCE_NONE);
        String message = resolveMessage(defaultStatus, adminDefaultId, hasAdminDefault, sources);

        List<DataSourceSelectionItem> items = sources
            .stream()
            .map(source -> toItem(source, normalizedCapability, adminDefaultId, selectedDefaultId))
            .sorted(
                Comparator
                    .comparing(DataSourceSelectionItem::recommended)
                    .reversed()
                    .thenComparing(DataSourceSelectionItem::defaultSource, Comparator.reverseOrder())
                    .thenComparing(item -> normalize(item.name()), Comparator.nullsLast(String::compareToIgnoreCase))
            )
            .toList();
        return new DataSourceSelectionResponse(normalizedCapability, selectedDefaultId, defaultSource, message, items);
    }

    private List<InfraDataSource> loadVisibleSources(String activeDeptHeader) {
        boolean isMaintainer = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
        boolean canViewAll = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        List<InfraDataSource> sources = canViewAll || isMaintainer
            ? dataSourceRepository.findAll()
            : dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        if (!canViewAll && isMaintainer) {
            String dept = normalizeDept(resolveActiveDept(activeDeptHeader));
            return sources
                .stream()
                .filter(source -> {
                    String owner = normalizeDept(source.getOwnerDept());
                    return owner.isEmpty() || (!dept.isEmpty() && owner.equalsIgnoreCase(dept));
                })
                .toList();
        }
        return sources;
    }

    private Optional<DefaultDestinationSyncService.DefaultDestinationStatus> resolveDefaultStatus() {
        try {
            return Optional.ofNullable(defaultDestinationSyncService.checkDefaultDestinationStatus());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String resolveMessage(
        DefaultDestinationSyncService.DefaultDestinationStatus defaultStatus,
        UUID adminDefaultId,
        boolean hasAdminDefault,
        List<InfraDataSource> sources
    ) {
        String statusMessage = defaultStatus != null ? normalize(defaultStatus.message()) : null;
        if (adminDefaultId != null && !hasAdminDefault) {
            return StringUtils.hasText(statusMessage)
                ? statusMessage
                : "默认湖仓未映射到可选 Platform 数据源，请检查 admin 默认湖仓与平台数据源配置";
        }
        if (adminDefaultId == null && defaultStatus != null && StringUtils.hasText(statusMessage)) {
            return statusMessage;
        }
        if (sources == null || sources.isEmpty()) {
            return "暂无可用 Platform 数据源";
        }
        return null;
    }

    private DataSourceSelectionItem toItem(
        InfraDataSource source,
        String requestedCapability,
        UUID adminDefaultId,
        UUID selectedDefaultId
    ) {
        List<String> capabilities = capabilities(source);
        boolean isAdminDefault = adminDefaultId != null && adminDefaultId.equals(source.getId());
        boolean recommended = selectedDefaultId != null && selectedDefaultId.equals(source.getId());
        String reason = isAdminDefault ? "admin-default" : (recommended ? "single-available" : null);
        return new DataSourceSelectionItem(
            source.getId(),
            source.getName(),
            source.getType(),
            source.getJdbcUrl(),
            source.getUsername(),
            source.getDescription(),
            source.getOwnerDept(),
            source.getStatus(),
            capabilities,
            capabilities.contains(requestedCapability),
            isAdminDefault,
            recommended,
            reason
        );
    }

    private boolean supportsCapability(InfraDataSource source, String capability) {
        return capabilities(source).contains(capability);
    }

    private List<String> capabilities(InfraDataSource source) {
        if (source == null || source.getId() == null || !STATUS_ACTIVE.equalsIgnoreCase(normalize(source.getStatus()))) {
            return List.of();
        }
        List<String> capabilities = new ArrayList<>();
        if (isJdbcSource(source)) {
            capabilities.add(CAPABILITY_QUERYABLE);
            capabilities.add(CAPABILITY_ANALYTICS_REGISTERABLE);
            capabilities.add(CAPABILITY_MODELING_LAKEHOUSE);
            capabilities.add(CAPABILITY_DBT_TARGET);
        }
        return capabilities;
    }

    private boolean isJdbcSource(InfraDataSource source) {
        if (!StringUtils.hasText(normalize(source.getJdbcUrl()))) {
            return false;
        }
        String type = normalizeLower(source.getType());
        return !StringUtils.hasText(type) || JDBC_TYPES.contains(type);
    }

    private String normalizeCapability(String capability) {
        String normalized = normalize(capability);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : CAPABILITY_MODELING_LAKEHOUSE;
    }

    private UUID parseUuid(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeLower(Object value) {
        String text = normalize(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private String normalizeDept(String dept) {
        return DepartmentUtils.normalize(dept);
    }

    private String resolveActiveDept(String activeDeptHeader) {
        String candidate = normalize(activeDeptHeader);
        if (StringUtils.hasText(candidate)) {
            return candidate;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                Object value = token.getToken().getClaims().get("dept_code");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
                value = token.getToken().getClaims().get("deptCode");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
                value = token.getToken().getClaims().get("department");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
            }
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                Object value = principal.getAttribute("dept_code");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
                value = principal.getAttribute("deptCode");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
                value = principal.getAttribute("department");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    public record DataSourceSelectionResponse(
        String capability,
        UUID defaultDataSourceId,
        String defaultSource,
        String message,
        List<DataSourceSelectionItem> items
    ) {}

    public record DataSourceSelectionItem(
        UUID id,
        String name,
        String type,
        String jdbcUrl,
        String username,
        String description,
        String ownerDept,
        String status,
        List<String> capabilities,
        boolean selectable,
        boolean defaultSource,
        boolean recommended,
        String recommendationReason
    ) {}
}
