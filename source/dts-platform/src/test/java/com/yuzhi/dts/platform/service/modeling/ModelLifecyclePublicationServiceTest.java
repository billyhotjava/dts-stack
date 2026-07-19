package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelLifecyclePublicationServiceTest {

    @Test
    void publishesTheApprovedContentRevisionWithoutFabricatingANewRevision() {
        ModelSpecRepository modelSpecs = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecSnapshotCodec codec = mock(ModelSpecSnapshotCodec.class);
        ModelLifecyclePublicationService service = new ModelLifecyclePublicationService(modelSpecs, lifecycle, codec);
        ModelSpecView draft = model(ModelStatus.DRAFT);
        ModelSpecView published = model(ModelStatus.PUBLISHED);
        LifecycleEventView event = event();
        Instant now = Instant.parse("2026-07-20T08:00:00Z");
        when(codec.toLifecycleView(draft, ModelStatus.PUBLISHED, 7, now)).thenReturn(published);
        when(codec.write(published)).thenReturn("published-snapshot");
        when(modelSpecs.compareAndSetLifecycle("tenant-a", "alice", 7, "a".repeat(64), ModelStatus.DRAFT, published)).thenReturn(1);
        when(modelSpecs.updateV2RevisionLifecycle("tenant-a", "alice", ModelStatus.DRAFT, published, "published-snapshot")).thenReturn(1);
        when(lifecycle.recordEvent(
            "tenant-a", "alice", published, EventType.RELEASE, "REGISTERING", "release-7", "approved", null,
            Map.of("approvedRevision", 7), now
        )).thenReturn(event);

        ModelLifecyclePublicationService.Publication result = service.publish(
            "tenant-a", "alice", draft, new PublishCommand("approved", "release-7"), now
        );

        assertThat(result.model().revision()).isEqualTo(7);
        assertThat(result.release()).isSameAs(event);
        verify(codec).toLifecycleView(draft, ModelStatus.PUBLISHED, 7, now);
    }

    private static ModelSpecView model(ModelStatus status) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(UUID.fromString("30000000-0000-0000-0000-000000000001"));
        when(model.planId()).thenReturn(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn("a".repeat(64));
        when(model.status()).thenReturn(status);
        return model;
    }

    private static LifecycleEventView event() {
        return new LifecycleEventView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            7,
            "a".repeat(64),
            EventType.RELEASE,
            "REGISTERING",
            "release-7",
            "alice",
            "approved",
            null,
            Map.of(),
            Instant.parse("2026-07-20T08:00:00Z")
        );
    }
}
