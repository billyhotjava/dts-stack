-- 现金收款单明细
select
    cast(s.id as bigint) as collection_id,
    cast(s.tenant_id as bigint) as tenant_id,
    cast(s.code as text) as collection_code,
    cast(s.project_id as bigint) as project_id,
    cast(s.project_name as text) as project_name,
    cast(s.income_type as integer) as income_type,
    cast(s.status as integer) as collection_status,
    cast(s.payment_type as integer) as payment_type,
    cast(s.pay_mode as text) as pay_mode,
    cast(s.pay_time as timestamp) as pay_time,
    cast(s.pay_amoney as numeric) as collection_amount,
    cast(s.update_time as timestamp) as update_time,
    cast(p.customer_id as bigint) as customer_id,
    cast(date_trunc('month', s.pay_time) as date) as collection_month,
    cast(coalesce(s.status = 2, false) as boolean) as is_confirmed,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsa_collection_record') }} s
left join {{ ref('prs_v2_dwd_project') }} p on s.project_id = p.project_id
