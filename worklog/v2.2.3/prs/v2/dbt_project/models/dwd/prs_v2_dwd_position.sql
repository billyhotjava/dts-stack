-- 摆放位置当前维度
select
    cast(s.id as bigint) as position_id,
    cast(s.project_id as bigint) as project_id,
    cast(s.floor_number_name as text) as floor_number_name,
    cast(s.floor_layer_name as text) as floor_layer_name,
    cast(s.region_full as text) as region_full,
    cast(s.alias_name as text) as alias_name,
    cast(s.contract_dept_id as bigint) as contract_dept_id,
    cast(s.curing_user_id as bigint) as curing_user_id,
    cast(s.curing_user_name as text) as curing_user_name,
    cast(s.status as integer) as status,
    cast(s.del_flag as text) as del_flag,
    cast(p.tenant_id as bigint) as tenant_id,
    cast(c.customer_id as bigint) as customer_id,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsp_position') }} s
left join {{ source('public', 'ods_prsp_project') }} p on s.project_id = p.id
left join {{ source('public', 'ods_prsp_contract') }} c on p.contract_id = c.id and p.tenant_id is not distinct from c.tenant_id
