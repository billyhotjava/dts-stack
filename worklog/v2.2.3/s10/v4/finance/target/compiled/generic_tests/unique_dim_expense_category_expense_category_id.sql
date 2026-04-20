

    
    

select
    expense_category_id as unique_field,
    count(*) as n_records

from "biadmin"."public"."dim_expense_category"
where expense_category_id is not null
group by expense_category_id
having count(*) > 1


