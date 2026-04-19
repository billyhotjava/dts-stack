

    
    

select
    summary_scope as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_ads_project_fund_kpi"
where summary_scope is not null
group by summary_scope
having count(*) > 1


