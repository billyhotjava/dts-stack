

    
    

with all_values as (

    select
        expense_category as value_field,
        count(*) as n_records

    from "biadmin"."public"."stg_fin__aux_balance"
    group by expense_category

)

select *
from all_values
where value_field not in (
    '原材料/设备','外协/服务','折旧','检测试验','设计咨询','租赁','培训','其他'
)


