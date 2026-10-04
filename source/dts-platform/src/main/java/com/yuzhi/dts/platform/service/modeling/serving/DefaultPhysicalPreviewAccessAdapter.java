package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewScope;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Uses existing ModelSpec visibility, Catalog read checks and modeling maintainer authorities. */
@Component
public class DefaultPhysicalPreviewAccessAdapter implements PhysicalPreviewAccessPort {

    private final CatalogModelServingProjectionRepository projections;
    private final CatalogDatasetRepository datasets;
    private final AccessChecker accessChecker;

    public DefaultPhysicalPreviewAccessAdapter(
        CatalogModelServingProjectionRepository projections,
        CatalogDatasetRepository datasets,
        AccessChecker accessChecker
    ) {
        this.projections = projections;
        this.datasets = datasets;
        this.accessChecker = accessChecker;
    }

    @Override
    public AccessContext authorize(
        String tenantId,
        String actorId,
        ModelSpecView model,
        PhysicalPreviewRequest request
    ) {
        if (request.scope() == PreviewScope.CANDIDATE) {
            if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
                throw denied();
            }
            // A candidate is anchored by the semantic ModelSpec key. Reusing the old serving
            // dataset would silently apply policies for a different physical result.
            return new AccessContext(null);
        }
        ModelServingProjection projection = projections
            .findProjection(tenantId, model.id())
            .orElseThrow(DefaultPhysicalPreviewAccessAdapter::denied);
        if (projection.servingRef() == null) throw denied();
        UUID physicalAssetId = projection.servingRef().physicalAssetId();
        CatalogDataset dataset = datasets.findById(physicalAssetId).orElseThrow(DefaultPhysicalPreviewAccessAdapter::denied);
        String activeDepartment = SecurityUtils.getCurrentUserDept().orElse(null);
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, activeDepartment)) {
            throw denied();
        }
        return new AccessContext(physicalAssetId);
    }

    private static PhysicalPreviewException denied() {
        return new PhysicalPreviewException("PHYSICAL_PREVIEW_ACCESS_DENIED", HttpStatus.FORBIDDEN, null);
    }
}
