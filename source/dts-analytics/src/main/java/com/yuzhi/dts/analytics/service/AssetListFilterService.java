package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.service.PlatformPermissionClient.AccessibleAssetsResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Filters asset lists based on platform permissions.
 * Used by CardResource, DashboardResource, ScreenResource to return only authorized assets.
 */
@Service
public class AssetListFilterService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetListFilterService.class);

    private static final String HEADER_USER = "X-DTS-User";
    private static final String HEADER_ROLES = "X-DTS-Roles";
    private static final String HEADER_DEPT = "X-DTS-Dept-Code";

    private final PlatformPermissionClient permissionClient;

    public AssetListFilterService(PlatformPermissionClient permissionClient) {
        this.permissionClient = permissionClient;
    }

    /**
     * Filter a list of entities by platform permission.
     *
     * @param entities   the full list of entities
     * @param assetType  asset type (CARD, DASHBOARD, SCREEN, TABLE, MODEL)
     * @param idExtractor function to extract asset ID from entity
     * @param request    HTTP request (for user context headers)
     * @return filtered list containing only authorized entities
     */
    public <T> List<T> filterByPermission(List<T> entities, String assetType,
                                           Function<T, String> idExtractor,
                                           HttpServletRequest request) {
        String username = request.getHeader(HEADER_USER);
        if (username == null || username.isBlank()) {
            // No user context, return all (backward compatibility during migration)
            return entities;
        }

        String roles = request.getHeader(HEADER_ROLES);
        String deptCode = request.getHeader(HEADER_DEPT);

        AccessibleAssetsResult accessible = permissionClient.listAccessibleAssetIds(
            username, roles, deptCode, assetType, 0, 10000
        );

        if (accessible.isAll()) {
            return entities;
        }

        Set<String> allowedIds = Set.copyOf(accessible.assetIds());
        return entities.stream()
            .filter(e -> allowedIds.contains(idExtractor.apply(e)))
            .collect(Collectors.toList());
    }
}
