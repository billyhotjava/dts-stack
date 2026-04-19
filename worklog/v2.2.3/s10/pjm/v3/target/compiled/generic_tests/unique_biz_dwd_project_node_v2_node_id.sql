

    
    

select
    node_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_project_node_v2"
where node_id is not null
group by node_id
having count(*) > 1


