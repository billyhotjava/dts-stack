-- biz_dwd_own_fund.sql
-- DWD层：自有资金明细，从year_period文本中解析period_year和period_type
-- 源表: public.own_fund

SELECT
    year_period,
    career_fund,
    career_note,
    deprec_fund,
    deprec_note,
    welfare_fund,
    safety_fund,
    total,
    SUBSTRING(year_period FROM '^\d{4}')::INT AS period_year,
    CASE
        WHEN year_period LIKE '%年初'     THEN 'opening'
        WHEN year_period LIKE '%预计增加' THEN 'increase'
        WHEN year_period LIKE '%预计使用' THEN 'usage'
        WHEN year_period LIKE '%余额'     THEN 'balance'
    END AS period_type
FROM public.own_fund
