package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogTagGovernanceGuard {

    private static final Set<String> MAINTAINER_AUTHORITIES = Set.copyOf(
        Arrays.asList(AuthoritiesConstants.CATALOG_MAINTAINERS)
    );

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requireMaintainer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !authentication.isAuthenticated() ||
            authentication instanceof AnonymousAuthenticationToken
        ) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                null,
                null,
                "请先登录后再执行数据标签治理操作"
            );
        }
        boolean allowed = authentication
            .getAuthorities()
            .stream()
            .anyMatch(authority -> MAINTAINER_AUTHORITIES.contains(authority.getAuthority()));
        if (!allowed) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                null,
                null,
                "当前用户不是数据目录治理角色"
            );
        }
    }
}
