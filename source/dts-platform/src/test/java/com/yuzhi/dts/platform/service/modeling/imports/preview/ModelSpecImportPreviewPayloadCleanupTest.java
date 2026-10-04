package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSpecImportPreviewPayloadCleanupTest {

    @Mock
    private ModelSpecImportPreviewRepository repository;

    @Test
    void redactsExpiredPayloadsWithoutAReadRequest() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        ModelSpecImportPreviewPayloadCleanup cleanup = new ModelSpecImportPreviewPayloadCleanup(
            repository,
            Clock.fixed(now, ZoneOffset.UTC)
        );

        cleanup.redactExpiredPayloads();

        verify(repository).redactExpiredPayloads(now);
    }
}
