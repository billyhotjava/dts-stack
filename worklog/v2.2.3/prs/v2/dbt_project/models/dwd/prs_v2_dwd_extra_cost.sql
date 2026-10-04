-- 报花附加费用明细
select
    cast(s.id as bigint) as extra_cost_id,
    cast(s.biz_id as bigint) as order_id,
    cast(s.biz_type as integer) as biz_type,
    cast(s.cost_type as integer) as cost_type,
    cast(s.title as text) as title,
    cast(s.free_amount as numeric) as cost_amount,
    cast(s.price_amount as numeric) as quoted_amount,
    cast(s.pay_time as timestamp) as pay_time,
    cast(s.create_time as timestamp) as create_time,
    cast(s.del_flag as text) as del_flag,
    cast(o.tenant_id as bigint) as tenant_id,
    cast(o.project_id as bigint) as project_id,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prst_flower_extra_cost') }} s
left join {{ ref('prs_v2_dwd_flower_order') }} o on s.biz_id = o.order_id
