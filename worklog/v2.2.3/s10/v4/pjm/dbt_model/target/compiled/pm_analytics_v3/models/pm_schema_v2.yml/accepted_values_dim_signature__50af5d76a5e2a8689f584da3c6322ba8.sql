
    
    

with all_values as (

    select
        context as value_field,
        count(*) as n_records

    from "biadmin"."public"."dim_signature_status_alias"
    group by context

)

select *
from all_values
where value_field not in (
    'I_II_no_review','I_II_with_review','III'
)


