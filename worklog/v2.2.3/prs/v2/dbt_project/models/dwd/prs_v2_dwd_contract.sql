-- 合同当前维度
select
    cast(s.id as bigint) as contract_id,
    cast(s.tenant_id as bigint) as tenant_id,
    cast(s.customer_id as bigint) as customer_id,
    cast(s.code as text) as contract_code,
    cast(s.title as text) as contract_name,
    cast(s.status as integer) as contract_status,
    cast(s.start_date as timestamp) as start_date,
    cast(s.end_date as timestamp) as end_date,
    cast(s.signing_time as timestamp) as signing_time,
    cast(s.month_settlement_money as numeric) as month_settlement_money,
    cast(s.amount_including_tax as numeric) as amount_including_tax,
    cast(s.del_flag as text) as del_flag,
    cast(s.update_time as timestamp) as update_time,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsp_contract') }} s
