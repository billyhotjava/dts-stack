package com.yuzhi.dts.platform.service.infra;

import org.springframework.util.StringUtils;

/**
 * Shared heuristic scoring for selecting the best data source candidate.
 *
 * <p>The core scoring rules (applied by {@link #scoreDataSource}) capture the
 * common pattern used across InfraManagementService, DbtConfigService,
 * and modeling materialization services. Callers that need additional
 * context-specific bonuses (e.g. description hints, preferred
 * database matching, admin-data-lake props) should add those on top of the
 * base score returned here.
 */
public final class DataSourceScorer {

	public static final String BIADMIN_NAME = "数仓 (biadmin)";

	private DataSourceScorer() {
		// utility class
	}

	/**
	 * Score a data source by name / jdbcUrl / type / status heuristics.
	 *
	 * <p>Higher score = better candidate for the platform default data lake.
	 *
	 * @param rawName   the data source name (may be {@code null})
	 * @param rawJdbcUrl the JDBC URL (may be {@code null})
	 * @param rawType   the data source type, e.g. "postgres" (may be {@code null})
	 * @param rawStatus the data source status, e.g. "ACTIVE" (may be {@code null})
	 * @return a non-negative heuristic score
	 */
	public static int scoreDataSource(String rawName, String rawJdbcUrl, String rawType, String rawStatus) {
		int score = 0;

		String name = lower(rawName);
		String jdbcUrl = lower(rawJdbcUrl);
		String type = lower(rawType);
		String status = lower(rawStatus);

		// ── Name-based signals ──────────────────────────────────────────
		if (BIADMIN_NAME.equalsIgnoreCase(rawName)) {
			score += 100;
		}
		if (name.contains("biadmin")) {
			score += 80;
		}

		// ── JDBC URL signals ────────────────────────────────────────────
		if (jdbcUrl.contains("/biadmin")) {
			score += 70;
		}

		// ── Type signals ────────────────────────────────────────────────
		if ("postgres".equals(type) || "postgresql".equals(type)) {
			score += 50;
		} else if (StringUtils.hasText(type)) {
			score += 20;
		}

		// ── Status / connectivity signals ───────────────────────────────
		if ("active".equals(status)) {
			score += 5;
		}
		if (StringUtils.hasText(jdbcUrl)) {
			score += 5;
		}

		return score;
	}

	// ── Internal helpers ────────────────────────────────────────────────

	private static String lower(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
