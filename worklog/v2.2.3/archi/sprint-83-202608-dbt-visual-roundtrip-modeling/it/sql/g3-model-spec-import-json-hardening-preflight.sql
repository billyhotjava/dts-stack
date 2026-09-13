WITH attempt_facts AS (
    SELECT attempt.id,
           attempt.summary_json,
           CASE
               WHEN pg_catalog.jsonb_typeof(attempt.selected_closure_json) = 'array'
                   THEN pg_catalog.jsonb_array_length(attempt.selected_closure_json)
           END AS selected,
           pg_catalog.count(result.id) AS terminal,
           pg_catalog.count(*) FILTER (WHERE result.status = 'CREATED') AS created,
           pg_catalog.count(*) FILTER (WHERE result.status = 'UPDATED') AS updated,
           pg_catalog.count(*) FILTER (WHERE result.status = 'SKIPPED') AS skipped,
           pg_catalog.count(*) FILTER (WHERE result.status = 'FAILED') AS failed,
           pg_catalog.count(*) FILTER (WHERE result.status = 'BLOCKED') AS blocked
      FROM modeling_model_spec_import_apply_attempt attempt
      LEFT JOIN modeling_model_spec_import_apply_result result
        ON result.attempt_id = attempt.id
     GROUP BY attempt.id
), closure_members AS (
    SELECT attempt.id AS attempt_id, closure.member
      FROM modeling_model_spec_import_apply_attempt attempt
      CROSS JOIN LATERAL pg_catalog.jsonb_array_elements_text(
          CASE
              WHEN pg_catalog.jsonb_typeof(attempt.selected_closure_json) = 'array'
                  THEN attempt.selected_closure_json
              ELSE '[]'::jsonb
          END
      ) AS closure(member)
), result_members AS (
    SELECT DISTINCT result.attempt_id, result.dbt_unique_id AS member
      FROM modeling_model_spec_import_apply_result result
), attempt_diagnostics AS (
    SELECT 'ATTEMPT'::text AS record_type,
           'attempt-' || pg_catalog.left(
               pg_catalog.md5(pg_catalog.current_database() || ':' || facts.id::text || ':sprint83-g3'),
               20
           ) AS attempt_ref,
           NULL::text AS result_ref,
           NULL::text AS dbt_unique_ref,
           CASE
               WHEN (
                   facts.summary_json IS NOT NULL
                   AND pg_catalog.jsonb_typeof(facts.summary_json) = 'object'
                   AND pg_catalog.jsonb_exists_all(facts.summary_json, ARRAY[
                       'selected', 'pending', 'succeeded', 'created',
                       'updated', 'skipped', 'failed', 'blocked'
                   ])
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'selected') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'pending') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'succeeded') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'created') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'updated') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'skipped') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'failed') = 'number'
                   AND pg_catalog.jsonb_typeof(facts.summary_json -> 'blocked') = 'number'
               ) IS NOT TRUE THEN true
               ELSE facts.summary_json ->> 'selected' <> facts.selected::text
                   OR facts.summary_json ->> 'pending' <> (facts.selected - facts.terminal)::text
                   OR facts.summary_json ->> 'succeeded' <> (facts.created + facts.updated)::text
                   OR facts.summary_json ->> 'created' <> facts.created::text
                   OR facts.summary_json ->> 'updated' <> facts.updated::text
                   OR facts.summary_json ->> 'skipped' <> facts.skipped::text
                   OR facts.summary_json ->> 'failed' <> facts.failed::text
                   OR facts.summary_json ->> 'blocked' <> facts.blocked::text
           END AS summary_mismatch,
           coalesce((
               SELECT pg_catalog.jsonb_agg(
                          'closure-ref-' || pg_catalog.left(
                              pg_catalog.md5(
                                  facts.id::text || ':' ||
                                  coalesce(missing.member, '<json-null>') || ':missing'
                              ),
                              20
                          )
                          ORDER BY missing.member
                      )
                 FROM (
                     SELECT closure.member
                       FROM closure_members closure
                      WHERE closure.attempt_id = facts.id
                     EXCEPT
                     SELECT result.member
                       FROM result_members result
                      WHERE result.attempt_id = facts.id
                 ) missing
           ), '[]'::jsonb)::text AS missing_closure_refs,
           coalesce((
               SELECT pg_catalog.jsonb_agg(
                          'closure-ref-' || pg_catalog.left(
                              pg_catalog.md5(
                                  facts.id::text || ':' ||
                                  coalesce(unexpected.member, '<json-null>') || ':unexpected'
                              ),
                              20
                          )
                          ORDER BY unexpected.member
                      )
                 FROM (
                     SELECT result.member
                       FROM result_members result
                      WHERE result.attempt_id = facts.id
                     EXCEPT
                     SELECT closure.member
                       FROM closure_members closure
                      WHERE closure.attempt_id = facts.id
                 ) unexpected
           ), '[]'::jsonb)::text AS unexpected_closure_refs,
           coalesce((
               SELECT pg_catalog.jsonb_agg(
                          'closure-ref-' || pg_catalog.left(
                              pg_catalog.md5(
                                  facts.id::text || ':' ||
                                  coalesce(duplicate.member, '<json-null>') || ':duplicate'
                              ),
                              20
                          )
                          ORDER BY duplicate.member
                      )
                 FROM (
                     SELECT closure.member
                       FROM closure_members closure
                      WHERE closure.attempt_id = facts.id
                     GROUP BY closure.member
                    HAVING pg_catalog.count(*) > 1 OR closure.member IS NULL
                 ) duplicate
           ), '[]'::jsonb)::text AS duplicate_closure_refs,
           '[]'::text AS reason_codes
      FROM attempt_facts facts
), result_reason_codes AS (
    SELECT result.id,
           result.attempt_id,
           pg_catalog.array_remove(ARRAY[
               CASE
                   WHEN result.merge_checkpoint_json IS NULL
                    AND (
                        result.accepted_external_checksum IS NOT NULL
                        OR result.accepted_implementation_revision IS NOT NULL
                        OR result.accepted_implementation_checksum IS NOT NULL
                        OR result.mapped_model_spec_revision IS NOT NULL
                        OR result.mapped_model_spec_etag IS NOT NULL
                    )
                       THEN 'CHECKPOINT_JSON_MISSING_WITH_RELATIONAL_FIELDS'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) IS DISTINCT FROM 'object'
                       THEN 'MERGE_CHECKPOINT_NOT_OBJECT'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'tenantId')
                       THEN 'MERGE_CHECKPOINT_MISSING_TENANT_ID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'tenantId')
                    AND (
                        pg_catalog.jsonb_typeof(result.merge_checkpoint_json -> 'tenantId') = 'string'
                        AND pg_catalog.btrim(result.merge_checkpoint_json ->> 'tenantId') <> ''
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_TENANT_ID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'projectKey')
                       THEN 'MERGE_CHECKPOINT_MISSING_PROJECT_KEY'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'projectKey')
                    AND (
                        pg_catalog.jsonb_typeof(result.merge_checkpoint_json -> 'projectKey') = 'string'
                        AND pg_catalog.btrim(result.merge_checkpoint_json ->> 'projectKey') <> ''
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_PROJECT_KEY'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'dbtUniqueId')
                       THEN 'MERGE_CHECKPOINT_MISSING_DBT_UNIQUE_ID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'dbtUniqueId')
                    AND (
                        pg_catalog.jsonb_typeof(result.merge_checkpoint_json -> 'dbtUniqueId') = 'string'
                        AND pg_catalog.btrim(result.merge_checkpoint_json ->> 'dbtUniqueId') <> ''
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_DBT_UNIQUE_ID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'acceptedExternalChecksum')
                       THEN 'MERGE_CHECKPOINT_MISSING_ACCEPTED_EXTERNAL_CHECKSUM'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'acceptedExternalChecksum')
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.merge_checkpoint_json -> 'acceptedExternalChecksum'
                        ) = 'string'
                        AND (result.merge_checkpoint_json ->> 'acceptedExternalChecksum') ~ '^[0-9a-f]{64}$'
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_ACCEPTED_EXTERNAL_CHECKSUM'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(
                        result.merge_checkpoint_json,
                        'acceptedImplementationRevision'
                    )
                       THEN 'MERGE_CHECKPOINT_MISSING_ACCEPTED_IMPLEMENTATION_REVISION'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(
                        result.merge_checkpoint_json,
                        'acceptedImplementationRevision'
                    )
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.merge_checkpoint_json -> 'acceptedImplementationRevision'
                        ) = 'number'
                        AND (
                            result.merge_checkpoint_json ->> 'acceptedImplementationRevision'
                        ) ~ '^[1-9][0-9]*$'
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_ACCEPTED_IMPLEMENTATION_REVISION'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(
                        result.merge_checkpoint_json,
                        'acceptedImplementationChecksum'
                    )
                       THEN 'MERGE_CHECKPOINT_MISSING_ACCEPTED_IMPLEMENTATION_CHECKSUM'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(
                        result.merge_checkpoint_json,
                        'acceptedImplementationChecksum'
                    )
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.merge_checkpoint_json -> 'acceptedImplementationChecksum'
                        ) = 'string'
                        AND (
                            result.merge_checkpoint_json ->> 'acceptedImplementationChecksum'
                        ) ~ '^[0-9a-f]{64}$'
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_ACCEPTED_IMPLEMENTATION_CHECKSUM'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'mappedModelSpecRevision')
                       THEN 'MERGE_CHECKPOINT_MISSING_MAPPED_MODEL_SPEC_REVISION'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'mappedModelSpecRevision')
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.merge_checkpoint_json -> 'mappedModelSpecRevision'
                        ) = 'number'
                        AND (
                            result.merge_checkpoint_json ->> 'mappedModelSpecRevision'
                        ) ~ '^[1-9][0-9]*$'
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_MAPPED_MODEL_SPEC_REVISION'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'mappedModelSpecEtag')
                       THEN 'MERGE_CHECKPOINT_MISSING_MAPPED_MODEL_SPEC_ETAG'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.merge_checkpoint_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.merge_checkpoint_json, 'mappedModelSpecEtag')
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.merge_checkpoint_json -> 'mappedModelSpecEtag'
                        ) = 'string'
                        AND (result.merge_checkpoint_json ->> 'mappedModelSpecEtag') ~ '^[0-9a-f]{64}$'
                    ) IS NOT TRUE
                       THEN 'MERGE_CHECKPOINT_INVALID_MAPPED_MODEL_SPEC_ETAG'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (
                        result.project_key IS NOT NULL
                        AND pg_catalog.btrim(result.project_key) <> ''
                    ) IS NOT TRUE
                       THEN 'CHECKPOINT_PROJECT_KEY_INVALID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (result.accepted_external_checksum ~ '^[0-9a-f]{64}$') IS NOT TRUE
                       THEN 'CHECKPOINT_ACCEPTED_EXTERNAL_CHECKSUM_INVALID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (result.accepted_implementation_revision > 0) IS NOT TRUE
                       THEN 'CHECKPOINT_ACCEPTED_IMPLEMENTATION_REVISION_INVALID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (result.accepted_implementation_checksum ~ '^[0-9a-f]{64}$') IS NOT TRUE
                       THEN 'CHECKPOINT_ACCEPTED_IMPLEMENTATION_CHECKSUM_INVALID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (result.mapped_model_spec_revision > 0) IS NOT TRUE
                       THEN 'CHECKPOINT_MAPPED_MODEL_SPEC_REVISION_INVALID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (result.mapped_model_spec_etag ~ '^[0-9a-f]{64}$') IS NOT TRUE
                       THEN 'CHECKPOINT_MAPPED_MODEL_SPEC_ETAG_INVALID'
               END,
               CASE
                   WHEN result.merge_checkpoint_json IS NOT NULL
                    AND (result.status IN ('CREATED', 'UPDATED', 'SKIPPED')) IS NOT TRUE
                       THEN 'CHECKPOINT_STATUS_INVALID'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) IS DISTINCT FROM 'object'
                       THEN 'PRE_PINS_NOT_OBJECT'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.pre_attempt_pins_json, 'modelRevision')
                       THEN 'PRE_PINS_MISSING_MODEL_REVISION'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.pre_attempt_pins_json, 'modelRevision')
                    AND (
                        pg_catalog.jsonb_typeof(result.pre_attempt_pins_json -> 'modelRevision') = 'number'
                        AND (result.pre_attempt_pins_json ->> 'modelRevision') ~ '^[1-9][0-9]*$'
                    ) IS NOT TRUE
                       THEN 'PRE_PINS_INVALID_MODEL_REVISION'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(result.pre_attempt_pins_json, 'modelChecksum')
                       THEN 'PRE_PINS_MISSING_MODEL_CHECKSUM'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND pg_catalog.jsonb_exists(result.pre_attempt_pins_json, 'modelChecksum')
                    AND (
                        pg_catalog.jsonb_typeof(result.pre_attempt_pins_json -> 'modelChecksum') = 'string'
                        AND (result.pre_attempt_pins_json ->> 'modelChecksum') ~ '^[0-9a-f]{64}$'
                    ) IS NOT TRUE
                       THEN 'PRE_PINS_INVALID_MODEL_CHECKSUM'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(
                        result.pre_attempt_pins_json,
                        'implementationRevision'
                    )
                       THEN 'PRE_PINS_MISSING_IMPLEMENTATION_REVISION'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND pg_catalog.jsonb_exists(
                        result.pre_attempt_pins_json,
                        'implementationRevision'
                    )
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.pre_attempt_pins_json -> 'implementationRevision'
                        ) = 'number'
                        AND (
                            result.pre_attempt_pins_json ->> 'implementationRevision'
                        ) ~ '^[1-9][0-9]*$'
                    ) IS NOT TRUE
                       THEN 'PRE_PINS_INVALID_IMPLEMENTATION_REVISION'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND NOT pg_catalog.jsonb_exists(
                        result.pre_attempt_pins_json,
                        'implementationChecksum'
                    )
                       THEN 'PRE_PINS_MISSING_IMPLEMENTATION_CHECKSUM'
               END,
               CASE
                   WHEN result.pre_attempt_pins_json IS NOT NULL
                    AND pg_catalog.jsonb_typeof(result.pre_attempt_pins_json) = 'object'
                    AND pg_catalog.jsonb_exists(
                        result.pre_attempt_pins_json,
                        'implementationChecksum'
                    )
                    AND (
                        pg_catalog.jsonb_typeof(
                            result.pre_attempt_pins_json -> 'implementationChecksum'
                        ) = 'string'
                        AND (
                            result.pre_attempt_pins_json ->> 'implementationChecksum'
                        ) ~ '^[0-9a-f]{64}$'
                    ) IS NOT TRUE
                       THEN 'PRE_PINS_INVALID_IMPLEMENTATION_CHECKSUM'
               END
           ], NULL) AS reason_codes
      FROM modeling_model_spec_import_apply_result result
), result_diagnostics AS (
    SELECT 'RESULT'::text AS record_type,
           'attempt-' || pg_catalog.left(
               pg_catalog.md5(
                   pg_catalog.current_database() || ':' || reasons.attempt_id::text || ':sprint83-g3'
               ),
               20
           ) AS attempt_ref,
           'result-' || pg_catalog.left(
               pg_catalog.md5(
                   pg_catalog.current_database() || ':' || reasons.id::text || ':sprint83-g3-result'
               ),
               20
           ) AS result_ref,
           'dbt-ref-' || pg_catalog.left(
               pg_catalog.md5(
                   pg_catalog.current_database() || ':' || reasons.id::text || ':sprint83-g3-dbt'
               ),
               20
           ) AS dbt_unique_ref,
           false AS summary_mismatch,
           '[]'::text AS missing_closure_refs,
           '[]'::text AS unexpected_closure_refs,
           '[]'::text AS duplicate_closure_refs,
           pg_catalog.to_jsonb(reasons.reason_codes)::text AS reason_codes
      FROM result_reason_codes reasons
     WHERE pg_catalog.cardinality(reasons.reason_codes) > 0
)
SELECT record_type,
       attempt_ref,
       result_ref,
       dbt_unique_ref,
       summary_mismatch,
       missing_closure_refs,
       unexpected_closure_refs,
       duplicate_closure_refs,
       reason_codes
  FROM attempt_diagnostics
 WHERE summary_mismatch
    OR missing_closure_refs <> '[]'
    OR unexpected_closure_refs <> '[]'
    OR duplicate_closure_refs <> '[]'
UNION ALL
SELECT record_type,
       attempt_ref,
       result_ref,
       dbt_unique_ref,
       summary_mismatch,
       missing_closure_refs,
       unexpected_closure_refs,
       duplicate_closure_refs,
       reason_codes
  FROM result_diagnostics
 ORDER BY record_type, attempt_ref, result_ref;
