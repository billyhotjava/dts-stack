package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Object-level read gate shared by data-quality query and export services. */
@Service
public class QualityDatasetReadGuard {

    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final AccessChecker accessChecker;
    private final DataStandardSecurity security;
    private final QualityEffectiveDepartmentResolver departmentResolver;

    public QualityDatasetReadGuard(
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        AccessChecker accessChecker,
        DataStandardSecurity security,
        QualityEffectiveDepartmentResolver departmentResolver
    ) {
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.accessChecker = accessChecker;
        this.security = security;
        this.departmentResolver = departmentResolver;
    }

    public CatalogDataset requireReadable(UUID datasetId, String activeDeptHeader) {
        CatalogDataset dataset = defaultLakeDatasetGuard.requireDefaultLakeDataset(datasetId);
        if (!isReadable(dataset, defaultLakeDatasetGuard.requireDefaultLakeSourceId(), activeDeptHeader)) {
            throw new AccessDeniedException("当前账号无权访问该数据集");
        }
        return dataset;
    }

    public Set<UUID> readableDatasetIds(Collection<CatalogDataset> datasets, String activeDeptHeader) {
        UUID defaultLakeSourceId = defaultLakeDatasetGuard.requireDefaultLakeSourceId();
        Set<UUID> readable = new HashSet<>();
        if (datasets == null) {
            return readable;
        }
        for (CatalogDataset dataset : datasets) {
            if (dataset != null && dataset.getId() != null && isReadable(dataset, defaultLakeSourceId, activeDeptHeader)) {
                readable.add(dataset.getId());
            }
        }
        return readable;
    }

    private boolean isReadable(CatalogDataset dataset, UUID defaultLakeSourceId, String activeDeptHeader) {
        if (!defaultLakeDatasetGuard.isDefaultLakeDataset(dataset, defaultLakeSourceId) || !accessChecker.canRead(dataset)) {
            return false;
        }
        if (security.hasInstituteScope()) {
            return true;
        }
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        return security.hasDepartmentScope() && accessChecker.departmentAllowed(dataset, activeDept);
    }
}
