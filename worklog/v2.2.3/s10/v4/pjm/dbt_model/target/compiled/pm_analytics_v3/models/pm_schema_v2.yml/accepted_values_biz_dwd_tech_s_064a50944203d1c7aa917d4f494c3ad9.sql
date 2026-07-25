
    
    

with all_values as (

    select
        change_category as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_tech_state_v2"
    group by change_category

)

select *
from all_values
where value_field not in (
    'I','II','III'
)


