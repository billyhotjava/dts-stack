
    
    

select
    budget_no as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_budget_v2"
where budget_no is not null
group by budget_no
having count(*) > 1


