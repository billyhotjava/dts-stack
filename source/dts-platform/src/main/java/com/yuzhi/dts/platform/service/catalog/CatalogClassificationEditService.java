package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.ModelingPermissionAudit;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogClassificationEditService {
    private final CatalogClassificationService classifications;
    private final CatalogClassificationWriteLock lock;
    private final EntityManager entities;
    private final ModelingPermissionAudit audit;
    public CatalogClassificationEditService(CatalogClassificationService classifications, CatalogClassificationWriteLock lock,
        EntityManager entities, ModelingPermissionAudit audit) {
        this.classifications = classifications; this.lock = lock; this.entities = entities; this.audit = audit;
    }
    public void lockCurrent(CatalogDataset dataset) {
        lock.lock("ASSET", CatalogAssetKey.dataset(dataset));
        entities.refresh(dataset, LockModeType.PESSIMISTIC_WRITE);
    }
    public void apply(CatalogDataset dataset, String requested, boolean provided) {
        if (!provided) return;
        String candidate = SecurityLevelCatalog.normalizeDataCode(requested);
        if (candidate == null) throw new ModelingIdentityException(400, "CLASSIFICATION_INVALID", "请选择有效密级，不能清空密级");
        String key = CatalogAssetKey.dataset(dataset);
        String current = SecurityLevelCatalog.maxDataCode(dataset.getClassification(), classifications.resolve("ASSET", key)
            .map(value -> value.getEffectiveLevel()).orElse(null));
        String actor = SecurityUtils.getCurrentUserId().orElseGet(() -> SecurityUtils.getCurrentUserLogin().orElse("unknown"));
        if (current != null && SecurityLevelCatalog.isDataDowngrade(current, candidate)) {
            audit.denied(actor, "CATALOG_CLASSIFICATION_DOWNGRADE_REJECTED", dataset.getId().toString(), "CLASSIFICATION_DOWNGRADE_FORBIDDEN");
            throw new ModelingIdentityException(409, "CLASSIFICATION_DOWNGRADE_FORBIDDEN", "资产密级只能调高，不能降低");
        }
        if (Objects.equals(candidate, current)) { dataset.setClassification(current); return; }
        var snapshot = classifications.sealOrRaise(new CatalogClassificationService.SealCommand("ASSET", key, "DATASET",
            null, null, candidate, List.of(), "MANUAL_FLOOR", "catalog-dataset:" + dataset.getId(),
            org.apache.commons.codec.digest.DigestUtils.sha256Hex(key + ":" + candidate), "{}"));
        dataset.setClassification(snapshot.getEffectiveLevel());
        audit.success(actor, "CATALOG_CLASSIFICATION_RAISE", dataset.getId().toString(), Map.of("classification", snapshot.getEffectiveLevel()));
    }
}
