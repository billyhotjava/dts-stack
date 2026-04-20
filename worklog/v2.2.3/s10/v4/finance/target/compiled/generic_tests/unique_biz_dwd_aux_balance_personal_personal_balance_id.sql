

    
    

select
    personal_balance_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_aux_balance_personal"
where personal_balance_id is not null
group by personal_balance_id
having count(*) > 1


