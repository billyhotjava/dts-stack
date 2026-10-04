
    
    

with all_values as (

    select
        status as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_quality_issue_v2"
    group by status

)

select *
from all_values
where value_field not in (
    '未完成归零','已完成技术归零','已完成管理归零','已完成技术和管理归零'
)


