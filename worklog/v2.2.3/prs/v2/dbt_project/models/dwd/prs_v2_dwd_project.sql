-- 项目当前维度
select
    cast(s.id as bigint) as project_id,
    cast(s.tenant_id as bigint) as tenant_id,
    cast(s.contract_id as bigint) as contract_id,
    cast(s.code as text) as project_code,
    cast(s.name as text) as project_name,
    cast(s.status as integer) as project_status,
    cast(s.manager_id as bigint) as manager_id,
    cast(s.biz_user_id as bigint) as biz_user_id,
    cast(s.curing_director as bigint) as curing_director,
    cast(s.start_time as timestamp) as start_time,
    cast(s.end_time as timestamp) as end_time,
    cast(s.del_flag as text) as del_flag,
    cast(s.update_time as timestamp) as update_time,
    cast(c.customer_id as bigint) as customer_id,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsp_project') }} s
left join {{ source('public', 'ods_prsp_contract') }} c on s.contract_id = c.id and s.tenant_id is not distinct from c.tenant_id
