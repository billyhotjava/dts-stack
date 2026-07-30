\set ON_ERROR_STOP on

BEGIN;

DO $repair$
DECLARE
    affected integer;
    canonical_count integer;
    old_url constant text := 'jdbc:postgresql://dts-pg:5432/biadmin1';
    new_url constant text := 'jdbc:postgresql://dts-pg:5432/biadmin';
BEGIN
    SELECT count(*)
      INTO canonical_count
      FROM infra_data_source
     WHERE id = 'a0000000-0000-0000-0000-000000000001'::uuid
       AND jdbc_url = new_url
       AND status = 'ACTIVE';
    IF canonical_count <> 1 THEN
        RAISE EXCEPTION 'canonical default lake expected 1 row, got %', canonical_count;
    END IF;

    UPDATE ingestion_task
       SET destination_config = jsonb_set(
               jsonb_set(destination_config, '{jdbcUrl}', to_jsonb(new_url), false),
               '{connection,0,jdbcUrl}',
               to_jsonb(new_url),
               false
           ),
           last_modified_by = 'ops-default-lake-repair',
           last_modified_date = CURRENT_TIMESTAMP
     WHERE id = 12
       AND name = 'findemo1'
       AND destination_config ->> 'targetDataSourceId'
           = 'a0000000-0000-0000-0000-000000000001'
       AND destination_config ->> 'jdbcUrl' = old_url
       AND destination_config #>> '{connection,0,jdbcUrl}' = old_url;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION 'ingestion_task repair expected 1 row, got %', affected;
    END IF;
END
$repair$;

COMMIT;

