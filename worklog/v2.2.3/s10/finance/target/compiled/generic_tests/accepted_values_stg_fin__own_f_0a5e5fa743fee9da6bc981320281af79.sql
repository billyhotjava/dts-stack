

    
    

with all_values as (

    select
        period_type as value_field,
        count(*) as n_records

    from "biadmin"."public"."stg_fin__own_fund"
    group by period_type

)

select *
from all_values
where value_field not in (
    'opening','increase','usage','balance'
)


