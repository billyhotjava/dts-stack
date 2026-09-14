-- 项目当前摆放汇总
select
    cast(md5(coalesce(cast(tenant_id as text), '<NULL>') || '|' || coalesce(cast(project_id as text), '<NULL>')) as text) as project_key,
    cast(tenant_id as bigint) as tenant_id,
    cast(project_id as bigint) as project_id,
    cast(count(*) as bigint) as placement_record_count,
    cast(count(distinct position_id) as bigint) as position_count,
    cast(sum(placement_quantity) filter (where good_type=1) as bigint) as plant_quantity,
    cast(sum(placement_quantity) filter (where good_type=2) as bigint) as pot_quantity,
    cast(sum(placement_quantity) filter (where good_type=3) as bigint) as rack_quantity,
    cast(sum(placement_quantity * unit_rent) as numeric) as placement_rent_amount,
    cast(sum(placement_quantity * unit_cost) as numeric) as placement_cost_amount,
    cast(count(*) filter (where good_type is null or good_type not in (1,2,3)) as bigint) as unknown_goods_type_count,
    cast(count(*) filter (where placement_quantity is null) as bigint) as missing_quantity_count,
    cast(count(*) filter (where unit_rent is null) as bigint) as missing_unit_rent_count,
    cast(max(snapshot_time) as timestamp) as snapshot_time
from {{ ref('prs_v2_dwd_placement') }}
where is_placed
group by tenant_id,project_id
