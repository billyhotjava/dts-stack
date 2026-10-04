
    
    

select
    completion_status_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_completion_status_v2"
where completion_status_id is not null
group by completion_status_id
having count(*) > 1


