package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits the ledger transition before external registration starts. */
@Service
public class ModelLifecyclePublicationService {

    private final ModelSpecRepository modelSpecs;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecSnapshotCodec codec;

    public ModelLifecyclePublicationService(
        ModelSpecRepository modelSpecs,
        ModelLifecycleRepository lifecycle,
        ModelSpecSnapshotCodec codec
    ) {
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.codec = codec;
    }

    @Transactional
    public Publication publish(
        String tenantId,
        String actorId,
        ModelSpecView current,
        PublishCommand command,
        Instant now
    ) {
        ModelSpecView published = codec.toLifecycleView(current, ModelStatus.PUBLISHED, current.revision(), now);
        transition(tenantId, actorId, current, ModelStatus.DRAFT, published);
        LifecycleEventView release = lifecycle.recordEvent(
            tenantId,
            actorId,
            published,
            EventType.RELEASE,
            "REGISTERING",
            command.idempotencyKey().trim(),
            command.comment(),
            null,
            Map.of("approvedRevision", current.revision()),
            now
        );
        return new Publication(published, release);
    }

    @Transactional
    public LifecycleEventView rollback(
        String tenantId,
        String actorId,
        ModelSpecView current,
        RollbackCommand command,
        Instant now
    ) {
        ModelSpecView draft = codec.toLifecycleView(current, ModelStatus.DRAFT, current.revision(), now);
        transition(tenantId, actorId, current, ModelStatus.PUBLISHED, draft);
        return lifecycle.recordEvent(
            tenantId,
            actorId,
            draft,
            EventType.ROLLBACK,
            "PASSED",
            command.idempotencyKey().trim(),
            command.comment(),
            null,
            Map.of("releasedRevision", current.revision()),
            now
        );
    }

    private void transition(
        String tenantId,
        String actorId,
        ModelSpecView current,
        ModelStatus expectedStatus,
        ModelSpecView replacement
    ) {
        int head = modelSpecs.compareAndSetLifecycle(
            tenantId,
            actorId,
            current.revision(),
            current.checksum(),
            expectedStatus,
            replacement
        );
        if (head == 0) throw conflict();
        int revision = modelSpecs.updateV2RevisionLifecycle(
            tenantId,
            actorId,
            expectedStatus,
            replacement,
            codec.write(replacement)
        );
        if (revision == 0) throw conflict();
    }

    private static ModelSpecException conflict() {
        return new ModelSpecException(
            "MODEL_RELEASE_CONFLICT",
            "ModelSpec changed before the lifecycle transition was committed",
            ModelSpecException.Kind.CONFLICT
        );
    }

    public record Publication(ModelSpecView model, LifecycleEventView release) {}
}
