

    
    

select
    dept_name as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dws_aux_balance_by_dept"
where dept_name is not null
group by dept_name
having count(*) > 1


