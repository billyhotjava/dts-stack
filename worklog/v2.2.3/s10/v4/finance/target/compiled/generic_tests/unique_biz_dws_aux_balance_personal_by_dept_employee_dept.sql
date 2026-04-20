

    
    

select
    employee_dept as unique_field,
    count(*) as n_records

from "biadmin"."public"."biz_dws_aux_balance_personal_by_dept"
where employee_dept is not null
group by employee_dept
having count(*) > 1


