package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.ListFilter;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single read path for canonical v2 ModelSpec snapshots. */
@Service
@Transactional(readOnly = true)
public class ModelSpecReader {

    private final ModelSpecRepository repository;
    private final ModelSpecSnapshotCodec codec;

    public ModelSpecReader(ModelSpecRepository repository, ModelSpecSnapshotCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    public ModelSpecView get(String tenantId, UUID modelSpecId) {
        return repository
            .findCurrent(tenantId, modelSpecId)
            .map(this::read)
            .orElseThrow(() -> notFound(modelSpecId));
    }

    public List<ModelSpecView> list(String tenantId, UUID planId, UUID domainId, ModelType modelType, Layer layer) {
        return repository.listCurrent(tenantId, new ListFilter(planId, domainId, modelType, layer)).stream().map(this::read).toList();
    }

    public ModelSpecView revision(String tenantId, ModelRevisionRef reference) {
        if (reference == null || reference.modelSpecId() == null || reference.revision() < 1) {
            throw new ModelSpecException(
                "MODEL_SPEC_REVISION_REF_INVALID",
                "ModelSpec revision reference is invalid",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        return repository
            .findRevision(tenantId, reference.modelSpecId(), reference.revision())
            .map(this::read)
            .orElseThrow(() -> notFound(reference.modelSpecId()));
    }

    public ModelSpecView read(StoredModelSpec stored) {
        requireCanonical(stored);
        return readCanonical(stored);
    }

    /** Decodes a bounded canonical relationship-graph window without compatibility overlays. */
    public List<ModelSpecView> readForRelationshipGraph(List<StoredModelSpec> storedModels) {
        if (storedModels == null || storedModels.isEmpty()) return List.of();
        if (storedModels.size() > 501) {
            throw new ModelSpecException(
                "MODEL_SPEC_RELATIONSHIP_GRAPH_WINDOW_INVALID",
                "Relationship graph ModelSpec window must not exceed 501",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        List<StoredModelSpec> rows = storedModels.stream().filter(Objects::nonNull).toList();
        String tenantId = null;
        for (StoredModelSpec stored : rows) {
            if (tenantId == null) {
                tenantId = stored.tenantId();
            } else if (!Objects.equals(tenantId, stored.tenantId())) {
                throw snapshotConflict("Relationship graph ModelSpec window mixes tenants");
            }
        }
        return rows.stream().map(this::read).toList();
    }

    private static void requireCanonical(StoredModelSpec stored) {
        if (stored != null && stored.contractVersion() == ModelSpecContract.CONTRACT_VERSION) return;
        throw new ModelSpecException(
            "MODEL_SPEC_CONTRACT_VERSION_UNSUPPORTED",
            "Stored ModelSpec contract version is unsupported",
            ModelSpecException.Kind.CONFLICT
        );
    }

    private ModelSpecView readCanonical(StoredModelSpec stored) {
        if (stored.currentSnapshot() == null || stored.currentSnapshot().isBlank()) {
            throw snapshotConflict("Canonical ModelSpec revision snapshot is missing");
        }
        ModelSpecView view = codec.readView(stored.currentSnapshot());
        if (
            view.contractVersion() != ModelSpecContract.CONTRACT_VERSION ||
            view.compatibilityMode() != CompatibilityMode.CANONICAL ||
            view.legacyRefs() != null ||
            !Objects.equals(view.id(), stored.id()) ||
            !Objects.equals(view.planId(), stored.planId()) ||
            !Objects.equals(view.domainId(), stored.domainId()) ||
            view.status() != stored.status() ||
            view.revision() != stored.revision() ||
            !codec.matchesStoredContentChecksum(stored.currentSnapshot(), view, view.checksum()) ||
            !Objects.equals(view.checksum(), stored.revisionChecksum()) ||
            (stored.currentHead() &&
                (stored.currentChecksum() == null ||
                    !Objects.equals(stored.currentChecksum(), stored.revisionChecksum())))
        ) {
            throw snapshotConflict("Canonical ModelSpec revision snapshot does not match the ledger head");
        }
        return view;
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "MODEL_SPEC_NOT_FOUND",
            "ModelSpec was not found",
            ModelSpecException.Kind.NOT_FOUND,
            id == null ? java.util.Map.of() : java.util.Map.of("modelSpecId", id)
        );
    }

    private static ModelSpecException snapshotConflict(String message) {
        return new ModelSpecException("MODEL_SPEC_SNAPSHOT_INVALID", message, ModelSpecException.Kind.CONFLICT);
    }
}
