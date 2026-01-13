package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient;
import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient.OrgNode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
        return cache.rootKeys().contains(deptCode.trim());
    }

    /**
     * Resolve a stable "institute/root" department identifier for default ownership.
     * <p>
     * Note: The platform uses org node IDs (as strings) as department identifiers in ABAC checks.
     */
    public Optional<String> resolveDefaultRootDept() {
        Cache cache = cacheRef.get();
        Instant now = Instant.now();
        if (cache == null || now.isAfter(cache.expiresAt())) {
            cache = reloadCache(now);
            cacheRef.set(cache);
        }
        if (StringUtils.hasText(cache.defaultRootDept())) {
            return Optional.of(cache.defaultRootDept().trim());
        }
        return Optional.empty();
    }

    public void evict() {
        cacheRef.set(null);
    }

    private Cache reloadCache(Instant now) {
        List<OrgNode> tree = adminDirectoryClient.fetchOrgTree();
        RootSnapshot snapshot = collectRoots(tree);
        return new Cache(snapshot.rootKeys(), snapshot.defaultRootDept(), now.plus(CACHE_TTL));
    }

    private RootSnapshot collectRoots(List<OrgNode> nodes) {
        Set<String> sink = new HashSet<>();
        String defaultDept = null;
        if (nodes != null && !nodes.isEmpty()) {
            defaultDept = collectRoots(nodes, sink, null);
        }
        return new RootSnapshot(sink, defaultDept);
    }

    private String collectRoots(List<OrgNode> nodes, Set<String> sink, String currentDefault) {
        if (nodes == null || nodes.isEmpty()) {
            return currentDefault;
        }
        for (OrgNode node : nodes) {
            if (node == null) {
                continue;
            }
            boolean flaggedRoot = Boolean.TRUE.equals(node.getIsRoot());
            boolean inferredRoot = node.getParentId() == null;
            if (flaggedRoot || inferredRoot) {
                String candidate = null;
                if (StringUtils.hasText(node.getDeptCode())) {
                    candidate = node.getDeptCode().trim();
                    sink.add(candidate);
                }
                if (node.getId() != null) {
                    String id = node.getId().toString().trim();
                    if (!id.isEmpty()) {
                        sink.add(id);
                        if (candidate == null) {
                            candidate = id;
                        }
                    }
                }
                if (candidate != null && currentDefault == null) {
                    currentDefault = candidate;
                }
            }
            currentDefault = collectRoots(node.getChildren(), sink, currentDefault);
        }
        return currentDefault;
    }

    private record RootSnapshot(Set<String> rootKeys, String defaultRootDept) {}

    private record Cache(Set<String> rootKeys, String defaultRootDept, Instant expiresAt) {}
}
