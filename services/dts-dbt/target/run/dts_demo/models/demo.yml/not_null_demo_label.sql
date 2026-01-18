
    select
      count(*) as failures,
      count(*) != 0 as should_warn,
      count(*) != 0 as should_error
    from (
      
    
  
    
    



select label
from "dts_platform"."public"."demo"
where label is null



  
  
      
    ) dbt_internal_test