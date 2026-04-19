

    
    

select
    issue_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_quality_issue_v2"
where issue_id is not null
group by issue_id
having count(*) > 1


