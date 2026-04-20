

    
    

select
    period_year as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_ads_own_fund_kpi"
where period_year is not null
group by period_year
having count(*) > 1


