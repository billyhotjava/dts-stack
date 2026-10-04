package com.yuzhi.dts.platform.service.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Compatibility wrapper around the new admin directory gateway.
 */
@Component
public class AdminDirectoryClient {

    private final AdminDirectoryGateway gateway;

    public AdminDirectoryClient(AdminDirectoryGateway gateway) {
        this.gateway = gateway;
    }

    public List<OrgNode> fetchOrgTree() {
        return gateway.fetchOrgTree().stream().map(this::mapNode).toList();
    }

    private OrgNode mapNode(AdminDirectoryGateway.OrgNode source) {
        OrgNode node = new OrgNode();
        node.setId(source.getId());
        node.setName(source.getName());
        node.setDeptCode(source.getDeptCode());
        node.setParentId(source.getParentId());
        node.setIsRoot(source.getIsRoot());
        if (source.getChildren() != null) {
            node.setChildren(source.getChildren().stream().map(this::mapNode).toList());
        }
        return node;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrgNode {
        private Long id;
        private String name;
        @JsonProperty("deptCode")
        private String deptCode;
        private Long parentId;
        private List<OrgNode> children;
        @JsonProperty("isRoot")
        private Boolean isRoot;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDeptCode() { return deptCode; }
        public void setDeptCode(String deptCode) { this.deptCode = deptCode; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public List<OrgNode> getChildren() { return children; }
        public void setChildren(List<OrgNode> children) { this.children = children; }
        public Boolean getIsRoot() { return isRoot; }
        public void setIsRoot(Boolean isRoot) { this.isRoot = isRoot; }
    }
}
