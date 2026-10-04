-- Sprint-31B F3/T05: normalize code asset lifecycle grant reasons for existing assets.
-- Idempotent: ownership/grant rows use their unique keys and are updated on conflict.

with indicator_assets as (
    select
        id::text as asset_id,
        coalesce(nullif(trim(code), ''), id::text) as natural_key,
        nullif(trim(owner_dept), '') as owner_dept,
        coalesce(nullif(trim(data_level), ''), 'PENDING_GOVERNANCE') as classification,
        case upper(replace(replace(coalesce(status, ''), '-', '_'), ' ', '_'))
            when 'DRAFT' then 'DRAFT_GOVERNANCE'
            when 'PENDING_APPROVAL' then 'DRAFT_GOVERNANCE'
            when 'APPROVED' then 'DRAFT_GOVERNANCE'
            when 'PUBLISHED' then 'ACTIVE'
            when 'ACTIVE' then 'ACTIVE'
            when 'PROMOTED' then 'ACTIVE'
            when 'TESTING' then 'TESTING'
            when 'ARCHIVED' then 'ARCHIVED'
            when 'RETIRED' then 'ARCHIVED'
            when 'DISABLED' then 'ARCHIVED'
            when 'DEPRECATED' then 'DEPRECATED'
            else 'PENDING_GOVERNANCE'
        end as lifecycle
    from gov_indicator_definition
    where owner_dept is not null and trim(owner_dept) <> ''
),
indicator_payload as (
    select
        'GOV_INDICATOR' as asset_type,
        asset_id,
        owner_dept,
        left('tenant:default/env:prod/dialect:generic/gov_indicator:' || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g'), 128) as source_id,
        left(
            'code asset sync; assetKey=tenant:default/env:prod/dialect:generic/gov_indicator:'
                || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g')
                || '; classification=' || classification
                || '; lifecycle=' || lifecycle,
            512
        ) as grant_reason
    from indicator_assets
)
insert into asset_ownership(asset_type, asset_id, owner_dept_code, source_id, assigned_by, created_by, created_date, last_modified_by, last_modified_date)
select asset_type, asset_id, owner_dept, source_id, 'dts-platform-backfill', 'system', now(), 'system', now()
from indicator_payload
on conflict (asset_type, asset_id) do update
   set owner_dept_code = excluded.owner_dept_code,
       source_id = excluded.source_id,
       assigned_by = excluded.assigned_by,
       last_modified_by = 'system',
       last_modified_date = now();

with indicator_assets as (
    select
        id::text as asset_id,
        coalesce(nullif(trim(code), ''), id::text) as natural_key,
        nullif(trim(owner_dept), '') as owner_dept,
        coalesce(nullif(trim(data_level), ''), 'PENDING_GOVERNANCE') as classification,
        case upper(replace(replace(coalesce(status, ''), '-', '_'), ' ', '_'))
            when 'DRAFT' then 'DRAFT_GOVERNANCE'
            when 'PENDING_APPROVAL' then 'DRAFT_GOVERNANCE'
            when 'APPROVED' then 'DRAFT_GOVERNANCE'
            when 'PUBLISHED' then 'ACTIVE'
            when 'ACTIVE' then 'ACTIVE'
            when 'PROMOTED' then 'ACTIVE'
            when 'TESTING' then 'TESTING'
            when 'ARCHIVED' then 'ARCHIVED'
            when 'RETIRED' then 'ARCHIVED'
            when 'DISABLED' then 'ARCHIVED'
            when 'DEPRECATED' then 'DEPRECATED'
            else 'PENDING_GOVERNANCE'
        end as lifecycle
    from gov_indicator_definition
    where owner_dept is not null and trim(owner_dept) <> ''
),
indicator_payload as (
    select
        'GOV_INDICATOR' as asset_type,
        asset_id,
        owner_dept,
        left(
            'code asset sync; assetKey=tenant:default/env:prod/dialect:generic/gov_indicator:'
                || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g')
                || '; classification=' || classification
                || '; lifecycle=' || lifecycle,
            512
        ) as grant_reason
    from indicator_assets
)
insert into asset_grant(asset_type, asset_id, grantee_type, grantee_id, permission, granted_by, grant_reason, created_by, created_date, last_modified_by, last_modified_date)
select asset_type, asset_id, 'DEPT', owner_dept, 'MANAGE', 'dts-platform-backfill', grant_reason, 'system', now(), 'system', now()
from indicator_payload
on conflict (asset_type, asset_id, grantee_type, grantee_id, permission) do update
   set grant_reason = excluded.grant_reason,
       granted_by = excluded.granted_by,
       last_modified_by = 'system',
       last_modified_date = now();

with glossary_assets as (
    select
        id::text as asset_id,
        coalesce(nullif(trim(code), ''), id::text) as natural_key,
        nullif(trim(owner_dept), '') as owner_dept,
        case upper(replace(replace(coalesce(status, ''), '-', '_'), ' ', '_'))
            when 'DRAFT' then 'DRAFT_GOVERNANCE'
            when 'PENDING_APPROVAL' then 'DRAFT_GOVERNANCE'
            when 'APPROVED' then 'DRAFT_GOVERNANCE'
            when 'PUBLISHED' then 'ACTIVE'
            when 'ACTIVE' then 'ACTIVE'
            when 'PROMOTED' then 'ACTIVE'
            when 'TESTING' then 'TESTING'
            when 'ARCHIVED' then 'ARCHIVED'
            when 'RETIRED' then 'ARCHIVED'
            when 'DISABLED' then 'ARCHIVED'
            when 'DEPRECATED' then 'DEPRECATED'
            else 'PENDING_GOVERNANCE'
        end as lifecycle
    from modeling_glossary_term
    where owner_dept is not null and trim(owner_dept) <> ''
),
glossary_payload as (
    select
        'GLOSSARY_TERM' as asset_type,
        asset_id,
        owner_dept,
        left('tenant:default/env:prod/dialect:generic/glossary_term:' || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g'), 128) as source_id,
        left(
            'code asset sync; assetKey=tenant:default/env:prod/dialect:generic/glossary_term:'
                || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g')
                || '; classification=INTERNAL'
                || '; lifecycle=' || lifecycle,
            512
        ) as grant_reason
    from glossary_assets
)
insert into asset_ownership(asset_type, asset_id, owner_dept_code, source_id, assigned_by, created_by, created_date, last_modified_by, last_modified_date)
select asset_type, asset_id, owner_dept, source_id, 'dts-platform-backfill', 'system', now(), 'system', now()
from glossary_payload
on conflict (asset_type, asset_id) do update
   set owner_dept_code = excluded.owner_dept_code,
       source_id = excluded.source_id,
       assigned_by = excluded.assigned_by,
       last_modified_by = 'system',
       last_modified_date = now();

with glossary_assets as (
    select
        id::text as asset_id,
        coalesce(nullif(trim(code), ''), id::text) as natural_key,
        nullif(trim(owner_dept), '') as owner_dept,
        case upper(replace(replace(coalesce(status, ''), '-', '_'), ' ', '_'))
            when 'DRAFT' then 'DRAFT_GOVERNANCE'
            when 'PENDING_APPROVAL' then 'DRAFT_GOVERNANCE'
            when 'APPROVED' then 'DRAFT_GOVERNANCE'
            when 'PUBLISHED' then 'ACTIVE'
            when 'ACTIVE' then 'ACTIVE'
            when 'PROMOTED' then 'ACTIVE'
            when 'TESTING' then 'TESTING'
            when 'ARCHIVED' then 'ARCHIVED'
            when 'RETIRED' then 'ARCHIVED'
            when 'DISABLED' then 'ARCHIVED'
            when 'DEPRECATED' then 'DEPRECATED'
            else 'PENDING_GOVERNANCE'
        end as lifecycle
    from modeling_glossary_term
    where owner_dept is not null and trim(owner_dept) <> ''
),
glossary_payload as (
    select
        'GLOSSARY_TERM' as asset_type,
        asset_id,
        owner_dept,
        left(
            'code asset sync; assetKey=tenant:default/env:prod/dialect:generic/glossary_term:'
                || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g')
                || '; classification=INTERNAL'
                || '; lifecycle=' || lifecycle,
            512
        ) as grant_reason
    from glossary_assets
)
insert into asset_grant(asset_type, asset_id, grantee_type, grantee_id, permission, granted_by, grant_reason, created_by, created_date, last_modified_by, last_modified_date)
select asset_type, asset_id, 'DEPT', owner_dept, 'MANAGE', 'dts-platform-backfill', grant_reason, 'system', now(), 'system', now()
from glossary_payload
on conflict (asset_type, asset_id, grantee_type, grantee_id, permission) do update
   set grant_reason = excluded.grant_reason,
       granted_by = excluded.granted_by,
       last_modified_by = 'system',
       last_modified_date = now();

with api_assets as (
    select
        id::text as asset_id,
        coalesce(nullif(trim(code), ''), id::text) as natural_key,
        coalesce(nullif(trim(classification), ''), 'PENDING_GOVERNANCE') as classification,
        case upper(replace(replace(coalesce(status, ''), '-', '_'), ' ', '_'))
            when 'DRAFT' then 'DRAFT_GOVERNANCE'
            when 'PENDING_APPROVAL' then 'DRAFT_GOVERNANCE'
            when 'APPROVED' then 'DRAFT_GOVERNANCE'
            when 'PUBLISHED' then 'ACTIVE'
            when 'ACTIVE' then 'ACTIVE'
            when 'PROMOTED' then 'ACTIVE'
            when 'TESTING' then 'TESTING'
            when 'ARCHIVED' then 'ARCHIVED'
            when 'RETIRED' then 'ARCHIVED'
            when 'DISABLED' then 'ARCHIVED'
            when 'DEPRECATED' then 'DEPRECATED'
            else 'PENDING_GOVERNANCE'
        end as lifecycle
    from svc_api
),
api_payload as (
    select
        'API_SERVICE' as asset_type,
        asset_id,
        'PLATFORM' as owner_dept,
        left('tenant:default/env:prod/dialect:generic/api_service:' || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g'), 128) as source_id,
        left(
            'code asset sync; assetKey=tenant:default/env:prod/dialect:generic/api_service:'
                || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g')
                || '; classification=' || classification
                || '; lifecycle=' || lifecycle,
            512
        ) as grant_reason
    from api_assets
)
insert into asset_ownership(asset_type, asset_id, owner_dept_code, source_id, assigned_by, created_by, created_date, last_modified_by, last_modified_date)
select asset_type, asset_id, owner_dept, source_id, 'dts-platform-backfill', 'system', now(), 'system', now()
from api_payload
on conflict (asset_type, asset_id) do update
   set owner_dept_code = excluded.owner_dept_code,
       source_id = excluded.source_id,
       assigned_by = excluded.assigned_by,
       last_modified_by = 'system',
       last_modified_date = now();

with api_assets as (
    select
        id::text as asset_id,
        coalesce(nullif(trim(code), ''), id::text) as natural_key,
        coalesce(nullif(trim(classification), ''), 'PENDING_GOVERNANCE') as classification,
        case upper(replace(replace(coalesce(status, ''), '-', '_'), ' ', '_'))
            when 'DRAFT' then 'DRAFT_GOVERNANCE'
            when 'PENDING_APPROVAL' then 'DRAFT_GOVERNANCE'
            when 'APPROVED' then 'DRAFT_GOVERNANCE'
            when 'PUBLISHED' then 'ACTIVE'
            when 'ACTIVE' then 'ACTIVE'
            when 'PROMOTED' then 'ACTIVE'
            when 'TESTING' then 'TESTING'
            when 'ARCHIVED' then 'ARCHIVED'
            when 'RETIRED' then 'ARCHIVED'
            when 'DISABLED' then 'ARCHIVED'
            when 'DEPRECATED' then 'DEPRECATED'
            else 'PENDING_GOVERNANCE'
        end as lifecycle
    from svc_api
),
api_payload as (
    select
        'API_SERVICE' as asset_type,
        asset_id,
        'PLATFORM' as owner_dept,
        left(
            'code asset sync; assetKey=tenant:default/env:prod/dialect:generic/api_service:'
                || regexp_replace(lower(natural_key), '[^a-z0-9_.:-]+', '_', 'g')
                || '; classification=' || classification
                || '; lifecycle=' || lifecycle,
            512
        ) as grant_reason
    from api_assets
)
insert into asset_grant(asset_type, asset_id, grantee_type, grantee_id, permission, granted_by, grant_reason, created_by, created_date, last_modified_by, last_modified_date)
select asset_type, asset_id, 'DEPT', owner_dept, 'MANAGE', 'dts-platform-backfill', grant_reason, 'system', now(), 'system', now()
from api_payload
on conflict (asset_type, asset_id, grantee_type, grantee_id, permission) do update
   set grant_reason = excluded.grant_reason,
       granted_by = excluded.granted_by,
       last_modified_by = 'system',
       last_modified_date = now();
