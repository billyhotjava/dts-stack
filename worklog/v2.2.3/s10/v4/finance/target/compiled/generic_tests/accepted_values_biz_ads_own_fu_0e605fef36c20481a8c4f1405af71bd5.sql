

    
    

with all_values as (

    select
        usage_rate_level as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_ads_own_fund_kpi"
    group by usage_rate_level

)

select *
from all_values
where value_field not in (
    'healthy','warning','danger'
)


