

    
    

select
    project_fund_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_project_fund"
where project_fund_id is not null
group by project_fund_id
having count(*) > 1


