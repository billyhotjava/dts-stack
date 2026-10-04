-- 回收业务行明细
select
    cast(s.id as bigint) as recovery_item_id,
    cast(s.recovery_info_id as bigint) as recovery_id,
    cast(s.biz_item_id as bigint) as order_item_id,
    cast(s.goods_price_id as bigint) as goods_price_id,
    cast(s.good_name as text) as good_name,
    cast(s.recovery_type as integer) as recovery_type,
    cast(s.recovery_number as integer) as planned_quantity,
    cast(s.real_recovery_number as integer) as actual_quantity,
    cast(s.good_cost as numeric) as good_cost,
    cast(s.status as integer) as item_status,
    cast(h.tenant_id as bigint) as tenant_id,
    cast(h.project_id as bigint) as project_id,
    cast(h.biz_info_id as bigint) as order_id,
    cast(h.status as integer) as recovery_status,
    cast(h.recovery_user_id as bigint) as recovery_user_id,
    cast(h.recovery_user_name as text) as recovery_user_name,
    cast(h.plan_recovery_time as timestamp) as plan_recovery_time,
    cast(coalesce(s.recovery_time,h.recovery_time) as timestamp) as recovery_time,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prst_recovery_info_item') }} s
left join {{ source('public', 'ods_prst_recovery_info') }} h on s.recovery_info_id = h.id
