package com.yuzhi.dts.platform.repository.modeling;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read model for QUALITY_PASSED candidates awaiting asynchronous review submission. */
@Repository
public class ModelPublicationReconciliationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelPublicationReconciliationRepository(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PendingReviewSubmission> findPendingReviewSubmissions(
        int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                "limit must be between 1 and 100"
            );
        }
        return jdbcTemplate.query(
            """
            select candidate.tenant_id,
                   candidate.id as candidate_id,
                   candidate.plan_id,
                   candidate.version as candidate_version,
                   publication.id as publication_event_id,
                   publication.actor_id as requester_actor_id
              from modeling_model_release_candidate candidate
              left join lateral (
                    select command.id, command.actor_id
                      from modeling_model_release_candidate_command command
                     where command.tenant_id = candidate.tenant_id
                       and command.candidate_id = candidate.id
                       and command.event_type = 'PUBLICATION_REQUESTED'
                     order by command.candidate_version desc, command.occurred_at desc
                     limit 1
              ) publication on true
             where candidate.status = 'QUALITY_PASSED'
             order by candidate.last_modified_date, candidate.id
             limit ?
            """,
            (row, rowNumber) ->
                new PendingReviewSubmission(
                    row.getString("tenant_id"),
                    row.getObject("candidate_id", UUID.class),
                    row.getObject("plan_id", UUID.class),
                    row.getInt("candidate_version"),
                    row.getObject("publication_event_id", UUID.class),
                    row.getString("requester_actor_id")
                ),
            limit
        );
    }

    public record PendingReviewSubmission(
        String tenantId,
        UUID candidateId,
        UUID planId,
        int candidateVersion,
        UUID publicationEventId,
        String requesterActorId
    ) {}
}
