

    
    

with all_values as (

    select
        growth_direction as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_ads_own_fund_kpi"
    group by growth_direction

)

select *
from all_values
where value_field not in (
    'growth','decline','flat'
)


