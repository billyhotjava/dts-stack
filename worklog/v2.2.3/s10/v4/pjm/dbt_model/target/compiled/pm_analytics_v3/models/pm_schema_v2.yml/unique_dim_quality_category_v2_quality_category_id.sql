
    
    

select
    quality_category_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_quality_category_v2"
where quality_category_id is not null
group by quality_category_id
having count(*) > 1


