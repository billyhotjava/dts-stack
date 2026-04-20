

    
    

select
    balance_direction_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_balance_direction"
where balance_direction_id is not null
group by balance_direction_id
having count(*) > 1


