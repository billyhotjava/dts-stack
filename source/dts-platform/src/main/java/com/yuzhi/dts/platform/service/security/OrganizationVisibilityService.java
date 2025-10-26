package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient;
import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient.OrgNode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lightweight cache around the organization tree to answer ROOT visibility checks.
 * A dataset owned by the ROOT department should be visible to all users.
 */
@Component
public class OrganizationVisibilityService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AdminDirectoryClient adminDirectoryClient;
    private final AtomicReference<Cache> cacheRef = new AtomicReference<>();

    public OrganizationVisibilityService(AdminDirectoryClient adminDirectoryClient) {
        this.adminDirectoryClient = adminDirectoryClient;
    }

    public boolean isRoot(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return false;
        }
        Cache cache = cacheRef.get();
        Instant now = Instant.now();
        if (cache == null || now.isAfter(cache.expiresAt())) {
            cache = reloadCache(now);
            cacheRef.set(cache);
        }
        return cache.rootIds().contains(deptCode.trim());
    }

    public void evict() {
        cacheRef.set(null);
    }

    private Cache reloadCache(Instant now) {
        List<OrgNode> tree = adminDirectoryClient.fetchOrgTree();
        Set<String> roots = new HashSet<>();
        collectRoots(tree, roots);
        return new Cache(roots, now.plus(CACHE_TTL));
    }

    private void collectRoots(List<OrgNode> nodes, Set<String> sink) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (OrgNode node : nodes) {
            if (node == null) {
                continue;
            }
            boolean flaggedRoot = Boolean.TRUE.equals(node.getIsRoot());
            boolean inferredRoot = node.getParentId() == null;
            if (flaggedRoot || inferredRoot) {
                if (node.getId() != null) {
                    sink.add(node.getId().toString().trim());
                }
            }
            collectRoots(node.getChildren(), sink);
        }
    }

    private record Cache(Set<String> rootIds, Instant expiresAt) {}
}
