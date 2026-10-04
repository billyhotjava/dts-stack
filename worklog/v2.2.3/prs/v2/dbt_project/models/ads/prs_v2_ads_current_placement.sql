-- 项目当前摆放分析
select
    cast(f.project_key as text) as project_key,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.placement_record_count as bigint) as placement_record_count,
    cast(f.position_count as bigint) as position_count,
    cast(f.plant_quantity as bigint) as plant_quantity,
    cast(f.pot_quantity as bigint) as pot_quantity,
    cast(f.rack_quantity as bigint) as rack_quantity,
    cast(f.placement_rent_amount as numeric) as placement_rent_amount,
    cast(f.placement_cost_amount as numeric) as placement_cost_amount,
    cast(f.unknown_goods_type_count as bigint) as unknown_goods_type_count,
    cast(f.missing_quantity_count as bigint) as missing_quantity_count,
    cast(f.missing_unit_rent_count as bigint) as missing_unit_rent_count,
    cast(f.snapshot_time as timestamp) as snapshot_time,
    cast(p.project_name as text) as project_name,
    cast(p.customer_id as bigint) as customer_id
from {{ ref('prs_v2_dws_project_current') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
