

    
    

select
    source_row_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."stg_fin__aux_balance_personal"
where source_row_id is not null
group by source_row_id
having count(*) > 1


