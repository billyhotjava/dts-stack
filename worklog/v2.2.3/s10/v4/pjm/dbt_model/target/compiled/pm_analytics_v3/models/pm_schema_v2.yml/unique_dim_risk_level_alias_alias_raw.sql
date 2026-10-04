
    
    

select
    alias_raw as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_risk_level_alias"
where alias_raw is not null
group by alias_raw
having count(*) > 1


