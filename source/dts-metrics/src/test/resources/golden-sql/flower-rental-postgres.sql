select
    stat_month as stat_month,
    cast(null as varchar) as customer_phone,
    sum(coalesce(contract_amount, 0)) as contract_amount
from dwd_flower_contract_detail
where
    (dept_code = 'D01')
group by
    stat_month,
    cast(null as varchar)
