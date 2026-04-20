

    
    

select
    own_fund_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_own_fund"
where own_fund_id is not null
group by own_fund_id
having count(*) > 1


