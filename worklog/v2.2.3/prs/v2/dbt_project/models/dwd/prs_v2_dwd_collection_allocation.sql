-- 收款关联分摊明细
select
    cast(s.id as bigint) as allocation_id,
    cast(s.collection_record_id as bigint) as collection_id,
    cast(s.biz_id as bigint) as linked_bill_id,
    cast(s.biz_type as integer) as linked_bill_type,
    cast(s.total_amount as numeric) as allocated_amount,
    cast(h.tenant_id as bigint) as tenant_id,
    cast(h.project_id as bigint) as project_id,
    cast(h.pay_time as timestamp) as pay_time,
    cast(coalesce(h.is_confirmed,false) as boolean) as is_confirmed,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsa_collection_item') }} s
left join {{ ref('prs_v2_dwd_finance_collection') }} h on s.collection_record_id = h.collection_id
