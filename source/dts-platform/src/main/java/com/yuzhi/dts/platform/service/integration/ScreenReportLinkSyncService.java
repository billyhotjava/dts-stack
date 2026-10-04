package com.yuzhi.dts.platform.service.integration;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.integration.dto.ScreenSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Sprint-17 / F1 — reconciles dts-analytics screens into platform-side
 * {@code bi_report_link} so leader-overview ("我常用的大屏") can include
 * dashboard ("大屏") visits without touching dts-bi internals.
 *
 * <p>Safety model:
 * <ul>
 *   <li>Only rows where {@code source = SCREEN_SYNC} are visible to this
 *       service. Manual entries ({@code source = MANUAL}) are never read,
 *       updated, or deleted by reconcile.</li>
 *   <li>Stale auto-synced rows (screen deleted upstream) are
 *       <em>soft-disabled</em> — never physically deleted — so the audit
 *       trail and existing visit history are preserved.</li>
 *   <li>If the client is not configured, reconcile is a no-op and the
 *       application continues to start.</li>
 * </ul>
 */
@Service
@Transactional
public class ScreenReportLinkSyncService {

    private static final Logger log = LoggerFactory.getLogger(ScreenReportLinkSyncService.class);

    public static final String SOURCE_TAG = "SCREEN_SYNC";
    public static final String CODE_PREFIX = "screen-";
    public static final String REPORT_TYPE = "SCREEN";
    public static final String ENGINE = "DTS_BI";
    /** Default classification when the upstream screen has none — leader-overview prefers a value. */
    public static final String DEFAULT_CLASSIFICATION = SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code();

    private final ScreenSyncClient client;
    private final BiReportLinkRepository repo;

    public ScreenReportLinkSyncService(ScreenSyncClient client, BiReportLinkRepository repo) {
        this.client = client;
        this.repo = repo;
    }

    public SyncResult reconcileOnce() {
        List<ScreenSummary> screens;
        try {
            screens = client.listScreens();
        } catch (NotConfiguredException e) {
            log.info("dts-analytics not configured, skipping screen reconcile");
            return SyncResult.ofSkipped();
        } catch (RestClientException e) {
            log.warn("dts-analytics list-screens failed: {}", e.getMessage());
            return SyncResult.ofFailed(e.getMessage());
        }

        Map<String, BiReportLink> existing = new HashMap<>();
        // Sprint-17.1: identify reconcile rows by code prefix rather than the `source`
        // column — the column may not exist in older DBs (Liquibase 20260425_02 not yet
        // applied) and we want the reconcile to remain functional regardless.
        for (BiReportLink link : repo.findAllByCodePrefix(CODE_PREFIX)) {
            existing.put(link.getCode(), link);
        }

        int created = 0;
        int updated = 0;
        int archived = 0;

        for (ScreenSummary s : screens) {
            if (s == null || s.id() == null) continue;
            String code = CODE_PREFIX + s.id();
            BiReportLink link = existing.remove(code);
            if (link == null) {
                repo.save(newLink(code, s));
                created++;
            } else if (applyUpdate(link, s)) {
                repo.save(link);
                updated++;
            }
        }

        for (BiReportLink stale : existing.values()) {
            if (stale.isEnabled()) {
                stale.setEnabled(false);
                repo.save(stale);
                archived++;
            }
        }

        return SyncResult.ofOk(created, updated, archived);
    }

    private static BiReportLink newLink(String code, ScreenSummary s) {
        BiReportLink l = new BiReportLink();
        l.setCode(code);
        l.setTitle(safeTitle(s));
        l.setEngine(ENGINE);
        l.setReportType(REPORT_TYPE);
        l.setUrl("/bi/screens/" + s.id() + "/preview");
        l.setClassification(safeClassification(s));
        l.setDeptCodes(s.ownerDeptCode());
        l.setEnabled(!s.archived());
        l.setSortOrder(0);
        // Sprint-17.1: source is now @Transient; the in-memory tag stays for any
        // consumer that introspects it but the DB no longer needs the column.
        l.setSource(SOURCE_TAG);
        return l;
    }

    private static boolean applyUpdate(BiReportLink l, ScreenSummary s) {
        boolean dirty = false;
        String title = safeTitle(s);
        if (!Objects.equals(l.getTitle(), title)) { l.setTitle(title); dirty = true; }

        String classification = safeClassification(s);
        if (!Objects.equals(l.getClassification(), classification)) {
            l.setClassification(classification);
            dirty = true;
        }

        if (!Objects.equals(l.getDeptCodes(), s.ownerDeptCode())) {
            l.setDeptCodes(s.ownerDeptCode());
            dirty = true;
        }

        boolean shouldEnable = !s.archived();
        if (l.isEnabled() != shouldEnable) {
            l.setEnabled(shouldEnable);
            dirty = true;
        }

        return dirty;
    }

    private static String safeTitle(ScreenSummary s) {
        String name = s.name();
        if (name == null || name.isBlank()) {
            return "screen-" + s.id();
        }
        return name;
    }

    private static String safeClassification(ScreenSummary s) {
        String c = s.classification();
        return (c == null || c.isBlank()) ? DEFAULT_CLASSIFICATION : c;
    }
}
