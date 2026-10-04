package com.yuzhi.dts.platform.service.integration;

/**
 * Sprint-17 / F1 — raised by {@link ScreenSyncClient} when
 * {@code dts.analytics.base-url} is empty or the integration is
 * explicitly disabled. The reconcile job catches this to skip a tick
 * without failing application startup.
 */
public class NotConfiguredException extends RuntimeException {

    public NotConfiguredException(String message) {
        super(message);
    }
}
