package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import org.springframework.stereotype.Service;

/** Applies the existing classification and department gates to owner-backed directory rows. */
@Service
public class CatalogAssetDirectoryVisibilityPolicy {

    private final AccessChecker accessChecker;

    public CatalogAssetDirectoryVisibilityPolicy(AccessChecker accessChecker) {
        this.accessChecker = accessChecker;
    }

    public boolean canRead(CatalogAssetDirectoryReadAdapter.OwnerAsset asset, String activeDept) {
        if (asset == null) {
            return false;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        CatalogDataset permissionView = new CatalogDataset();
        permissionView.setId(asset.resourceId());
        permissionView.setName(asset.displayName());
        permissionView.setClassification(asset.classification());
        permissionView.setEnabled(true);
        if (!accessChecker.canRead(permissionView)) {
            return false;
        }
        String[] ownerDepartments = ownerDepartments(asset.ownerDept());
        if (ownerDepartments.length == 0) {
            permissionView.setOwnerDept(null);
            return accessChecker.departmentAllowedExact(permissionView, activeDept);
        }
        for (String ownerDepartment : ownerDepartments) {
            permissionView.setOwnerDept(ownerDepartment);
            if (accessChecker.departmentAllowedExact(permissionView, activeDept)) {
                return true;
            }
        }
        return false;
    }

    private String[] ownerDepartments(String ownerDept) {
        if (ownerDept == null || ownerDept.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays
            .stream(ownerDept.replace('[', ' ').replace(']', ' ').replace('"', ' ').split("[,;]"))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toArray(String[]::new);
    }
}
