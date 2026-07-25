
    
    

select
    risk_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_risk_info_v2"
where risk_id is not null
group by risk_id
having count(*) > 1


