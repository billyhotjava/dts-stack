-- 商品规格当前维度
select
    cast(s.id as bigint) as goods_price_id,
    cast(s.goods_id as bigint) as goods_id,
    cast(s.goods_code as text) as goods_code,
    cast(s.goods_name as text) as goods_name,
    cast(s.specifications as text) as specifications,
    cast(s.goods_type as integer) as goods_type,
    cast(s.unit as text) as unit,
    cast(s.guidance_price as numeric) as guidance_price,
    cast(s.cost_price as numeric) as cost_price,
    cast(s.status as integer) as status,
    cast(s.del_flag as text) as del_flag,
    cast(g.tenant_id as bigint) as tenant_id,
    cast(g.goods_classify_id as bigint) as goods_classify_id,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsb_goods_price') }} s
left join {{ source('public', 'ods_prsb_goods') }} g on s.goods_id = g.id
