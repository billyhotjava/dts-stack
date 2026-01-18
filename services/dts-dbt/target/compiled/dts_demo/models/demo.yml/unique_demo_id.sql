
    
    

select
    id as unique_field,
    count(*) as n_records

from "dts_platform"."public"."demo"
where id is not null
group by id
having count(*) > 1


