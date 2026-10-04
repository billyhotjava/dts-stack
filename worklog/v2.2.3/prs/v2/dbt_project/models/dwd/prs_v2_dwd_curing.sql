-- 养护记录单头明细
select
    cast(s.id as bigint) as curing_id,
    cast(s.tenant_id as bigint) as tenant_id,
    cast(s.project_id as bigint) as project_id,
    cast(s.curing_user_id as bigint) as curing_user_id,
    cast(s.curing_user_name as text) as curing_user_name,
    cast(s.curing_time as timestamp) as curing_time,
    cast(s.total_position_number as integer) as position_quantity,
    cast(s.record_type as integer) as record_type,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prst_curing_record') }} s
