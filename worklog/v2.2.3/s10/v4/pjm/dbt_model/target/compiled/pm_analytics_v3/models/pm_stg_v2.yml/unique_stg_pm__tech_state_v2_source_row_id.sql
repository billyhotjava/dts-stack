
    
    

select
    source_row_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."stg_pm__tech_state_v2"
where source_row_id is not null
group by source_row_id
having count(*) > 1


