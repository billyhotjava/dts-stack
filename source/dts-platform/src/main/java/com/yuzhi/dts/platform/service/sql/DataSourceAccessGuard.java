package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralised access check for SQL workbench operations against an infra data source.
 * Rules:
 *   - Privileged roles (ADMIN / OP_ADMIN / INST_*) can access any data source.
 *   - A data source with no {@code ownerDept} is treated as institute-wide and is open
 *     to any authenticated user.
 *   - Otherwise the caller's active department (taken from {@code X-Active-Dept} or
 *     the JWT claim) must match the data source's {@code ownerDept}.
 */
@Component
public class DataSourceAccessGuard {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceAccessGuard.class);

    private final InfraDataSourceRepository dataSourceRepository;

    public DataSourceAccessGuard(InfraDataSourceRepository dataSourceRepository) {
        this.dataSourceRepository = dataSourceRepository;
    }

    /** Throw 403 if the current user cannot access the data source identified by {@code datasourceId}. */
    public void assertReadable(UUID datasourceId, String activeDept) {
        if (datasourceId == null) {
            return;
        }
        if (isPrivileged()) {
            return;
        }
        InfraDataSource ds = dataSourceRepository.findById(datasourceId).orElse(null);
        if (ds == null) {
            // Resolution happens downstream (e.g. default data-lake); let that layer produce 404.
            return;
        }
        String owner = ds.getOwnerDept();
        if (!StringUtils.hasText(owner)) {
            return; // institute-wide data source
        }
        if (!DepartmentUtils.matches(owner, activeDept)) {
            LOG.warn(
                "datasource access denied user={} datasource={} owner={} activeDept={}",
                SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
                datasourceId,
                owner,
                activeDept
            );
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该数据源");
        }
    }

    /** Accept either a UUID string or null; no-ops when {@code datasource} is not a UUID. */
    public void assertReadable(String datasource, String activeDept) {
        if (!StringUtils.hasText(datasource)) {
            return;
        }
        try {
            assertReadable(UUID.fromString(datasource), activeDept);
        } catch (IllegalArgumentException ignored) {
            // Non-UUID (e.g. legacy catalog name) — leave to downstream resolution
        }
    }

    private boolean isPrivileged() {
        for (String role : AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES) {
            if (SecurityUtils.hasCurrentUserAnyOfAuthorities(role)) {
                return true;
            }
        }
        return false;
    }
}
