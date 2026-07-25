
    
    

select
    budget_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_budget_v2"
where budget_id is not null
group by budget_id
having count(*) > 1


