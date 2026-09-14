-- 客户当前维度
select
    cast(s.id as bigint) as customer_id,
    cast(s.tenant_id as bigint) as tenant_id,
    cast(s.code as text) as customer_code,
    cast(s.name as text) as customer_name,
    cast(s.abbreviation as text) as customer_abbreviation,
    cast(s.type as text) as customer_type,
    cast(s.status as text) as customer_status,
    cast(s.del_flag as text) as del_flag,
    cast(s.update_time as timestamp) as update_time,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsp_customer') }} s
