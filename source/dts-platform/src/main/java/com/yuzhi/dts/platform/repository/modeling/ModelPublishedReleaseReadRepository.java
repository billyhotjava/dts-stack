package com.yuzhi.dts.platform.repository.modeling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * F15 K1: the latest published release per model, read from the local lifecycle ledger only.
 * Published history stays with its own revision, so a newer draft or a failed build never hides it.
 */
@Repository
public class ModelPublishedReleaseReadRepository {

    public static final int MAX_MODELS = 50;

    private final JdbcTemplate jdbcTemplate;

    public ModelPublishedReleaseReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PublishedRelease(UUID releaseId, UUID modelSpecId, int modelRevision, String environment, Instant publishedAt) {}

    public Map<UUID, PublishedRelease> latestPublished(String tenantId, Collection<UUID> modelSpecIds) {
        if (modelSpecIds == null || modelSpecIds.isEmpty()) return Map.of();
        if (modelSpecIds.size() > MAX_MODELS) {
            throw new IllegalArgumentException("At most " + MAX_MODELS + " models per request");
        }
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(modelSpecIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(modelSpecIds.size(), "?"));
        Map<UUID, PublishedRelease> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
            select distinct on (model_spec_id)
                   id, model_spec_id, model_revision, details_json ->> 'environment' as environment, created_date
              from modeling_model_lifecycle_event
             where tenant_id = ?
               and model_spec_id in (%s)
               and event_type = 'RELEASE'
               and status = 'PUBLISHED'
             order by model_spec_id, created_date desc, id desc
            """.formatted(placeholders),
            row -> {
                UUID modelSpecId = row.getObject("model_spec_id", UUID.class);
                result.put(
                    modelSpecId,
                    new PublishedRelease(
                        row.getObject("id", UUID.class),
                        modelSpecId,
                        row.getInt("model_revision"),
                        row.getString("environment"),
                        row.getTimestamp("created_date").toInstant()
                    )
                );
            },
            arguments.toArray()
        );
        return result;
    }
}
