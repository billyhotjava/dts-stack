

    
    

with all_values as (

    select
        completion_status as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_project_node_v2"
    group by completion_status

)

select *
from all_values
where value_field not in (
    '正常待完成','按时完成','超期已完成已变更','超期已完成未变更','不正常待变更','超期未完成未变更','超期未完成已变更'
)


