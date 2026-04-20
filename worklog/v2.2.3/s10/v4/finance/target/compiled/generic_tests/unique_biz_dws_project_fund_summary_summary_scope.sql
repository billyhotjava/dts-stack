

    
    

select
    summary_scope as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dws_project_fund_summary"
where summary_scope is not null
group by summary_scope
having count(*) > 1


