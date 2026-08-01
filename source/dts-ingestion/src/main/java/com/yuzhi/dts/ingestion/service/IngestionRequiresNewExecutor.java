package com.yuzhi.dts.ingestion.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Opens an actual independent transaction for post-commit and idempotent callback work. */
@Component
public class IngestionRequiresNewExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T execute(Supplier<T> work) {
        if (work == null) {
            throw new IllegalArgumentException("transaction work is required");
        }
        return work.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeWithoutResult(Runnable work) {
        if (work == null) {
            throw new IllegalArgumentException("transaction work is required");
        }
        work.run();
    }
}
