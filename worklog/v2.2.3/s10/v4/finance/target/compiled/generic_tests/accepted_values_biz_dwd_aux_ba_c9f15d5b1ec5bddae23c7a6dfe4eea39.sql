

    
    

with all_values as (

    select
        subject_category as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_aux_balance_personal"
    group by subject_category

)

select *
from all_values
where value_field not in (
    '其他应收-借款','应付职工薪酬','其他'
)


