
    
    

select
    change_category_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_change_category_v2"
where change_category_id is not null
group by change_category_id
having count(*) > 1


