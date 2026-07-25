
    
    

with all_values as (

    select
        node_type as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_project_node_v2"
    group by node_type

)

select *
from all_values
where value_field not in (
    '一般节点','重要节点','重大节点','里程碑节点'
)


