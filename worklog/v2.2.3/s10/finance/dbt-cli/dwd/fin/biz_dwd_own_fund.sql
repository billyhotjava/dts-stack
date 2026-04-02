{{ config(materialized='table', tags=['finance', 'biz', 'dwd']) }}

-- ============================================================
-- 自有资金 DWD 层：解析 year_period 文本，提取年度和期间类型
-- 输入：fin_ods.ods_finance_own_fund（原始自有资金表）
-- 输出：结构化的年度 + 期间类型 + 各基金金额（NUMERIC）
-- 注意：CSV 导入后所有列为 TEXT，此处统一转型
-- ============================================================

SELECT
    -- === 原始字段（数值列转型） ===
    year_period,
    career_fund::NUMERIC(15,2)  AS career_fund,
    career_note,
    deprec_fund::NUMERIC(15,2)  AS deprec_fund,
    deprec_note,
    welfare_fund::NUMERIC(15,2) AS welfare_fund,
    safety_fund::NUMERIC(15,2)  AS safety_fund,
    total::NUMERIC(15,2)        AS total,

    -- === 派生字段：从 year_period 文本中解析年度和期间类型 ===
    SUBSTRING(year_period FROM '^\d{4}')::INT AS period_year,

    CASE
        WHEN year_period LIKE '%年初'     THEN 'opening'
        WHEN year_period LIKE '%预计增加' THEN 'increase'
        WHEN year_period LIKE '%预计使用' THEN 'usage'
        WHEN year_period LIKE '%余额'     THEN 'balance'
    END AS period_type,

    -- === 期间排序号，方便下游按逻辑顺序排列 ===
    CASE
        WHEN year_period LIKE '%年初'     THEN 1
        WHEN year_period LIKE '%预计增加' THEN 2
        WHEN year_period LIKE '%预计使用' THEN 3
        WHEN year_period LIKE '%余额'     THEN 4
    END AS period_sort

FROM {{ source('fin_ods', 'ods_finance_own_fund') }}
WHERE year_period IS NOT NULL
