

    
    

select
    signature_status_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_signature_status_v2"
where signature_status_id is not null
group by signature_status_id
having count(*) > 1


