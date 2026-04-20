

    
    

with all_values as (

    select
        balance_direction as value_field,
        count(*) as n_records

    from "biadmin"."public"."stg_fin__aux_balance_personal"
    group by balance_direction

)

select *
from all_values
where value_field not in (
    'debit','credit','zero'
)


