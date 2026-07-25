package com.yuzhi.dts.platform.service.modeling.imports.preview;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically removes expired import payloads even when no user reopens a preview run. */
@Component
public class ModelSpecImportPreviewPayloadCleanup {

    private final ModelSpecImportPreviewRepository repository;
    private final Clock clock;

    @Autowired
    public ModelSpecImportPreviewPayloadCleanup(ModelSpecImportPreviewRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ModelSpecImportPreviewPayloadCleanup(ModelSpecImportPreviewRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.model-import.preview-cleanup-delay-ms:300000}",
        initialDelayString = "${dts.modeling.model-import.preview-cleanup-initial-delay-ms:300000}"
    )
    public void redactExpiredPayloads() {
        repository.redactExpiredPayloads(clock.instant());
    }
}
