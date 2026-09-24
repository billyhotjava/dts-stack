package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.StandardCoverage;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Manual administration of the platform-global model governance policy (F13-T09).
 *
 * <p>The gate only changes through {@link #update}, i.e. an explicit administrator action; nothing in the
 * platform switches it to BLOCKING by itself. Candidates that already froze a quality snapshot keep the
 * policy they were checked under.
 */
@Service
public class ModelGovernancePolicyAdministrationService {

    static final String SYSTEM_ACTOR = "system-migration";

    private static final String UNFROZEN_STATUSES = "'BUILDING', 'BUILT', 'QUALITY_RUNNING', 'QUALITY_FAILED'";
    private static final String FROZEN_STATUSES = "'QUALITY_PASSED', 'REVIEW_PENDING', 'APPROVED', 'PUBLISHING'";

    private final JdbcTemplate jdbc;

    public ModelGovernancePolicyAdministrationService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    public record PolicyView(
        QualityGate qualityGate,
        StandardCoverage standardCoverage,
        int revision,
        String lastModifiedBy,
        Instant lastModifiedDate,
        boolean systemDefault
    ) {}

    /** Candidates that will be (re)checked under the new gate versus those whose frozen snapshot is kept. */
    public record ImpactView(long unfrozenCandidates, long frozenCandidates) {}

    public static class PolicyRevisionConflictException extends RuntimeException {

        public PolicyRevisionConflictException() {
            super("模型发布治理策略已被他人修改，请刷新后重试");
        }
    }

    public static class PolicyMissingException extends RuntimeException {

        public PolicyMissingException() {
            super("平台模型发布治理策略不存在");
        }
    }

    public PolicyView current() {
        return jdbc
            .query(
                """
                select quality_gate, standard_coverage, revision, last_modified_by, last_modified_date
                  from modeling_platform_governance_policy
                 where policy_key = 'PLATFORM_DEFAULT'
                """,
                (row, rowNumber) -> {
                    String lastModifiedBy = row.getString("last_modified_by");
                    return new PolicyView(
                        QualityGate.valueOf(row.getString("quality_gate")),
                        StandardCoverage.valueOf(row.getString("standard_coverage")),
                        row.getInt("revision"),
                        lastModifiedBy,
                        row.getTimestamp("last_modified_date").toInstant(),
                        SYSTEM_ACTOR.equals(lastModifiedBy)
                    );
                }
            )
            .stream()
            .findFirst()
            .orElseThrow(PolicyMissingException::new);
    }

    public ImpactView impact() {
        return new ImpactView(countCandidates(UNFROZEN_STATUSES), countCandidates(FROZEN_STATUSES));
    }

    public PolicyView update(QualityGate qualityGate, int expectedRevision, String actor) {
        Objects.requireNonNull(qualityGate, "qualityGate is required");
        if (!StringUtils.hasText(actor)) {
            throw new IllegalArgumentException("actor is required");
        }
        if (SYSTEM_ACTOR.equalsIgnoreCase(actor.trim())) {
            throw new IllegalArgumentException("actor must be a person");
        }
        int updated = jdbc.update(
            """
            update modeling_platform_governance_policy
               set quality_gate = ?,
                   revision = revision + 1,
                   last_modified_by = ?,
                   last_modified_date = current_timestamp
             where policy_key = 'PLATFORM_DEFAULT'
               and revision = ?
            """,
            qualityGate.name(),
            actor.trim(),
            expectedRevision
        );
        if (updated == 0) {
            current();
            throw new PolicyRevisionConflictException();
        }
        return current();
    }

    private long countCandidates(String statuses) {
        Long count = jdbc.queryForObject(
            "select count(*) from modeling_model_release_candidate where status in (" + statuses + ")",
            Long.class
        );
        return count == null ? 0L : count;
    }
}
