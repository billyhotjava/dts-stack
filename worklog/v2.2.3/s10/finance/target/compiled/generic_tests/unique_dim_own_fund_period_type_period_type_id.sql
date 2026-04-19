

    
    

select
    period_type_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_own_fund_period_type"
where period_type_id is not null
group by period_type_id
having count(*) > 1


