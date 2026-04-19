

    
    

with all_values as (

    select
        issue_category as value_field,
        count(*) as n_records

    from "biadmin"."public"."biz_dwd_quality_issue_v2"
    group by issue_category

)

select *
from all_values
where value_field not in (
    '设计','工艺','管理','元器件','操作','外协','软件','环境','其他'
)


