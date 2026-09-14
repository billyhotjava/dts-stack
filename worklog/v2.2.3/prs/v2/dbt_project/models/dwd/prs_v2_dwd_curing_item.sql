-- 养护楼层行明细
select
    cast(s.id as bigint) as curing_item_id,
    cast(s.curing_record_id as bigint) as curing_id,
    cast(s.floor_layer_id as bigint) as floor_layer_id,
    cast(s.floor_layer_full_name as text) as floor_layer_full_name,
    cast(s.curing_content as text) as curing_content,
    cast(s.remarks as text) as remarks,
    cast(h.tenant_id as bigint) as tenant_id,
    cast(h.project_id as bigint) as project_id,
    cast(h.curing_user_id as bigint) as curing_user_id,
    cast(h.curing_time as timestamp) as curing_time,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prst_curing_record_item') }} s
left join {{ ref('prs_v2_dwd_curing') }} h on s.curing_record_id = h.curing_id
