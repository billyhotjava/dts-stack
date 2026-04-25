package com.yuzhi.dts.platform.service.integration;

/**
 * Sprint-17 / F1 — outcome of a single
 * {@link ScreenReportLinkSyncService#reconcileOnce()} pass.
 *
 * <ul>
 *   <li>{@code created} — new rows inserted (previously unseen screen ids)</li>
 *   <li>{@code updated} — existing rows whose mirror fields drifted</li>
 *   <li>{@code archived} — rows soft-disabled because the upstream screen is gone or archived</li>
 *   <li>{@code skipped} — true when client was not configured; nothing was attempted</li>
 *   <li>{@code error} — non-null when the upstream call or persistence failed</li>
 * </ul>
 */
public record SyncResult(int created, int updated, int archived, boolean skipped, String error) {

    public static SyncResult ofOk(int created, int updated, int archived) {
        return new SyncResult(created, updated, archived, false, null);
    }

    public static SyncResult ofSkipped() {
        return new SyncResult(0, 0, 0, true, null);
    }

    public static SyncResult ofFailed(String error) {
        return new SyncResult(0, 0, 0, false, error);
    }
}
