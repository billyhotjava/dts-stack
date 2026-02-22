package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.directory.AdminDirectoryClient;
import com.yuzhi.dts.platform.service.directory.AdminUserDirectoryClient;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/indicators")
@Transactional(readOnly = true)
public class GovernanceIndicatorDependencyResource {

    private final AdminDirectoryClient adminDirectoryClient;
    private final AdminUserDirectoryClient adminUserDirectoryClient;
    private final CatalogDomainRepository domainRepository;

    public GovernanceIndicatorDependencyResource(
        AdminDirectoryClient adminDirectoryClient,
        AdminUserDirectoryClient adminUserDirectoryClient,
        CatalogDomainRepository domainRepository
    ) {
        this.adminDirectoryClient = adminDirectoryClient;
        this.adminUserDirectoryClient = adminUserDirectoryClient;
        this.domainRepository = domainRepository;
    }

    /**
     * Indicator-management dependencies:
     * - Departments & users are proxied from dts-admin (via platform directory clients)
     * - Subject domains are platform-local catalog domains
     */
    @GetMapping("/dependencies")
    public ApiResponse<Map<String, Object>> dependencies(
        @RequestParam(name = "userKeyword", required = false) String userKeyword,
        @RequestParam(name = "includeUsers", defaultValue = "false") boolean includeUsers
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orgTree", adminDirectoryClient.fetchOrgTree());
        payload.put("domainTree", buildDomainTree(domainRepository.findAll()));

        if (includeUsers || StringUtils.hasText(userKeyword)) {
            payload.put("users", adminUserDirectoryClient.searchUsers(userKeyword));
        } else {
            payload.put("users", List.of());
        }

        payload.put("statusOptions", List.of("DRAFT", "PUBLISHED", "ARCHIVED"));
        payload.put(
            "dataLevelOptions",
            java.util.Arrays.stream(DataLevel.values()).map(Enum::name).toList()
        );
        return ApiResponses.ok(payload);
    }

    private List<Map<String, Object>> buildDomainTree(List<CatalogDomain> all) {
        Map<UUID, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (CatalogDomain domain : all) {
            if (domain == null || domain.getId() == null) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", domain.getId());
            node.put("name", domain.getName());
            node.put("code", domain.getCode());
            node.put("owner", domain.getOwner());
            node.put("description", domain.getDescription());
            node.put("parentId", domain.getParent() != null ? domain.getParent().getId() : null);
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(domain.getId(), node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodeMap.values()) {
            UUID parentId = (UUID) node.get("parentId");
            if (parentId != null && nodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
