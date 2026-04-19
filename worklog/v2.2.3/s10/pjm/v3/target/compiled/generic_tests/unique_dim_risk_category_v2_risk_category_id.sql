

    
    

select
    risk_category_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_risk_category_v2"
where risk_category_id is not null
group by risk_category_id
having count(*) > 1


