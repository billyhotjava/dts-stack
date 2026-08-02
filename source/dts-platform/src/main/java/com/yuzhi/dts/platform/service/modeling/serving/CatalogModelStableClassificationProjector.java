package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.SealRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Projects the admitted revision classification onto the stable semantic Catalog identity. */
@Component
public class CatalogModelStableClassificationProjector {

    private final CatalogClassificationBoundary classifications;

    public CatalogModelStableClassificationProjector(CatalogClassificationBoundary classifications) {
        this.classifications = classifications;
    }

    public void project(ModelSpecView model, ImplementationView implementation, String triggerRef) {
        String sourceKey = sourceKey(model, implementation);
        ClassificationFact source = requirePropagated("ASSET", sourceKey);
        String stableKey = CatalogAssetKey.semanticModel(model.id().toString());
        String tableLevel = normalizeLevel(source.effectiveLevel());
        classifications.sealOrRaise(
            new SealRequest(
                "ASSET",
                stableKey,
                "SEMANTIC_MODEL",
                null,
                null,
                null,
                List.of(tableLevel),
                "MODEL_PUBLICATION_PROJECTION",
                triggerRef,
                sha256(model.id() + ":" + model.revision() + ":asset:" + tableLevel),
                "{\"sourceSubjectKey\":\"" + escape(sourceKey) + "\",\"modelRevision\":" + model.revision() + "}"
            )
        );
        for (ModelField field : model.fields()) {
            if (field.name() == null || field.name().isBlank()) {
                throw new IllegalStateException("CATALOG_MODEL_COLUMN_CLASSIFICATION_INVALID");
            }
            String sourceColumnKey = sourceKey + "/column:" + normalize(field.name());
            ClassificationFact sourceColumn = classifications.resolve("COLUMN", sourceColumnKey).orElse(null);
            if (sourceColumn != null && !sourceColumn.propagated()) {
                throw new IllegalStateException("CATALOG_MODEL_COLUMN_CLASSIFICATION_NOT_READY");
            }
            List<String> inherited = new ArrayList<>();
            inherited.add(tableLevel);
            if (sourceColumn != null) inherited.add(normalizeLevel(sourceColumn.effectiveLevel()));
            String declared = field.securityLevel() == null || field.securityLevel().isBlank()
                ? null
                : normalizeLevel(field.securityLevel());
            classifications.sealOrRaise(
                new SealRequest(
                    "COLUMN",
                    stableKey + "/column:" + normalize(field.name()),
                    "SEMANTIC_MODEL",
                    declared,
                    null,
                    null,
                    inherited,
                    "MODEL_PUBLICATION_PROJECTION",
                    triggerRef,
                    sha256(model.id() + ":" + model.revision() + ":column:" + field.name() + ":" + inherited + ":" + declared),
                    "{\"sourceSubjectKey\":\"" + escape(sourceColumnKey) + "\",\"field\":\"" + escape(field.name()) + "\"}"
                )
            );
        }
    }

    private ClassificationFact requirePropagated(String subjectType, String subjectKey) {
        ClassificationFact fact = classifications.resolve(subjectType, subjectKey).orElse(null);
        if (fact == null || !fact.propagated() || fact.effectiveLevel() == null) {
            throw new IllegalStateException("CATALOG_MODEL_CLASSIFICATION_NOT_READY");
        }
        return fact;
    }

    private static String sourceKey(ModelSpecView model, ImplementationView implementation) {
        if (implementation.dbtUniqueId() != null && !implementation.dbtUniqueId().isBlank()) {
            return CatalogAssetKey.dbtModel(implementation.dbtUniqueId(), model.name());
        }
        return "model-spec:" + model.id() + ":revision:" + model.revision();
    }

    private static String normalizeLevel(String value) {
        try {
            return SecurityLevelCatalog.requireDataLevel(value).code();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("CATALOG_MODEL_CLASSIFICATION_INVALID", invalid);
        }
    }

    private static String normalize(String value) {
        return value
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("CATALOG_MODEL_CLASSIFICATION_CHECKSUM_FAILED", failure);
        }
    }

    private static String escape(String value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
