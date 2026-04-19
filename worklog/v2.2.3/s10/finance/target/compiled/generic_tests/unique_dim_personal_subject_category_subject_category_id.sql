

    
    

select
    subject_category_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_personal_subject_category"
where subject_category_id is not null
group by subject_category_id
having count(*) > 1


