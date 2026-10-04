
    
    

select
    quality_status_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_quality_status_v2"
where quality_status_id is not null
group by quality_status_id
having count(*) > 1


