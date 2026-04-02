-- biz_ads_aux_balance_kpi.sql
-- ADS层：辅助余额最终KPI，包含汇总指标及按科目/部门/费用类别/合同的聚合，供大屏直接消费
-- 依赖: biz_dwd_aux_balance

SELECT
    -- KPI指标
    SUM(balance)                                                     AS total_balance,
    COUNT(DISTINCT subject_code)                                     AS subject_count,
    COUNT(DISTINCT CASE
        WHEN contract_name IS NOT NULL AND contract_name != '—'
        THEN contract_name
    END)                                                             AS contract_count,
    COUNT(DISTINCT dept_name)                                        AS dept_count,

    -- 按科目TOP10(JSON)
    (SELECT json_agg(t) FROM (
        SELECT subject_code || ' ' || subject_name AS label, SUM(balance) AS value
        FROM biz_dwd_aux_balance
        GROUP BY subject_code, subject_name
        ORDER BY value DESC
        LIMIT 10
    ) t)                                                             AS top10_by_subject,

    -- 按部门分布(JSON)
    (SELECT json_agg(t) FROM (
        SELECT dept_name AS label, SUM(balance) AS value
        FROM biz_dwd_aux_balance
        GROUP BY dept_name
        ORDER BY value DESC
    ) t)                                                             AS by_dept,

    -- 按费用类别分布(JSON)
    (SELECT json_agg(t) FROM (
        SELECT expense_category AS label, SUM(balance) AS value
        FROM biz_dwd_aux_balance
        GROUP BY expense_category
        ORDER BY value DESC
    ) t)                                                             AS by_category,

    -- 合同金额TOP8(JSON)
    (SELECT json_agg(t) FROM (
        SELECT contract_name AS contract, dept_name AS dept, SUM(balance) AS balance
        FROM biz_dwd_aux_balance
        WHERE contract_name IS NOT NULL AND contract_name != '—'
        GROUP BY contract_name, dept_name
        ORDER BY balance DESC
        LIMIT 8
    ) t)                                                             AS top8_contracts

FROM biz_dwd_aux_balance
