-- 报花变更明细
select
    cast(s.id as bigint) as change_id,
    cast(s.biz_id as bigint) as order_id,
    cast(s.code as text) as change_code,
    cast(s.change_type as integer) as change_type,
    cast(s.status as integer) as change_status,
    cast(s.change_number as integer) as change_quantity,
    cast(s.before_good_price_id as bigint) as before_good_price_id,
    cast(s.after_good_price_id as bigint) as after_good_price_id,
    cast(s.before_total_amount as numeric) as before_total_amount,
    cast(s.after_total_amount as numeric) as after_total_amount,
    cast(s.apply_time as timestamp) as apply_time,
    cast(s.confirmed_time as timestamp) as confirmed_time,
    cast(o.tenant_id as bigint) as tenant_id,
    cast(o.project_id as bigint) as project_id,
    cast(o.customer_id as bigint) as customer_id,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prst_change_info') }} s
left join {{ ref('prs_v2_dwd_flower_order') }} o on s.biz_id = o.order_id
