

    
    

select
    risk_level_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_risk_level_v2"
where risk_level_id is not null
group by risk_level_id
having count(*) > 1


