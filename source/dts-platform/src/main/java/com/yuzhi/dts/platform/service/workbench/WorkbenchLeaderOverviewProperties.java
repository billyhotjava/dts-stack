package com.yuzhi.dts.platform.service.workbench;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sprint-15 P2-1 / P2-2 — Tunables for the workbench leader-overview path.
 *
 * <p>Lifts hard-coded constants ({@code TOP_N}, {@code DOMAIN_MATRIX_TOP},
 * MINE look-back windows) and the previously-system-default timezone out
 * of {@link WorkbenchLeaderOverviewService} so operators can adjust them
 * per environment without recompiling. Bind via:
 *
 * <pre>
 * app:
 *   workbench:
 *     leader-overview:
 *       top-n: 10
 *       domain-matrix-top: 6
 *       mine-reports-lookback-days: 30
 *       mine-top-reports-lookback-days: 90
 *       timezone: Asia/Shanghai
 * </pre>
 *
 * <p>All defaults match the original constants so an upgrade is a no-op for
 * any environment that does not configure the new keys.
 */
@ConfigurationProperties(prefix = "app.workbench.leader-overview")
public class WorkbenchLeaderOverviewProperties {

    private int topN = 10;
    private int domainMatrixTop = 6;
    private int mineReportsLookbackDays = 30;
    private int mineTopReportsLookbackDays = 90;
    /** Timezone used for all month/quarter/year boundary computations. */
    private String timezone = "Asia/Shanghai";

    public int getTopN() {
        return topN > 0 ? topN : 10;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public int getDomainMatrixTop() {
        return domainMatrixTop > 0 ? domainMatrixTop : 6;
    }

    public void setDomainMatrixTop(int domainMatrixTop) {
        this.domainMatrixTop = domainMatrixTop;
    }

    public int getMineReportsLookbackDays() {
        return mineReportsLookbackDays > 0 ? mineReportsLookbackDays : 30;
    }

    public void setMineReportsLookbackDays(int mineReportsLookbackDays) {
        this.mineReportsLookbackDays = mineReportsLookbackDays;
    }

    public int getMineTopReportsLookbackDays() {
        return mineTopReportsLookbackDays > 0 ? mineTopReportsLookbackDays : 90;
    }

    public void setMineTopReportsLookbackDays(int mineTopReportsLookbackDays) {
        this.mineTopReportsLookbackDays = mineTopReportsLookbackDays;
    }

    public String getTimezone() {
        return timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
