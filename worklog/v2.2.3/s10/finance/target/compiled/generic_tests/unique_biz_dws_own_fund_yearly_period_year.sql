

    
    

select
    period_year as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dws_own_fund_yearly"
where period_year is not null
group by period_year
having count(*) > 1


