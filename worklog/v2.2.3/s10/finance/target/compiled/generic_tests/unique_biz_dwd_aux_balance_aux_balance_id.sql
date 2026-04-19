

    
    

select
    aux_balance_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_aux_balance"
where aux_balance_id is not null
group by aux_balance_id
having count(*) > 1


