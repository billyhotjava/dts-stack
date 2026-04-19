

    
    

select
    tech_state_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dwd_tech_state_v2"
where tech_state_id is not null
group by tech_state_id
having count(*) > 1


