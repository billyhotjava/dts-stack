package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.SealReference;
import org.springframework.stereotype.Service;

@Service
public class CatalogClassificationAdmissionService {

    private final CatalogClassificationService classificationService;

    public CatalogClassificationAdmissionService(CatalogClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    public CatalogClassificationSnapshot requireValid(SealReference seal) {
        if (seal == null) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_SEAL_REQUIRED",
                "A classification seal is required before production persistence"
            );
        }
        CatalogClassificationSnapshot snapshot = classificationService
            .resolve(seal.subjectType(), seal.subjectKey())
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CLASSIFICATION_SEAL_NOT_FOUND",
                    "The referenced classification seal does not exist"
                )
            );
        boolean valid =
            snapshot.getId().equals(seal.sealId()) &&
            snapshot.getEvidenceChecksum().equalsIgnoreCase(seal.checksum()) &&
            snapshot.getRecordVersion() != null &&
            snapshot.getRecordVersion().longValue() == seal.snapshotVersion() &&
            CatalogClassificationService.STATUS_PROPAGATED.equals(snapshot.getPropagationStatus());
        if (!valid) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_SEAL_STALE",
                "The classification seal is stale or its evidence checksum does not match"
            );
        }
        return snapshot;
    }
}
