
    
    

with all_values as (

    select
        risk_category as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_risk_info_v2"
    group by risk_category

)

select *
from all_values
where value_field not in (
    '技术','进度','成本','设计','质量','其他'
)


