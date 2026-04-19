

    
    

with all_values as (

    select
        risk_level as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_risk_info_v2"
    group by risk_level

)

select *
from all_values
where value_field not in (
    '高','中','低'
)


