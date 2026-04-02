-- biz_ads_aux_balance_personal_kpi.sql
-- ADS层：个人辅助余额最终KPI，包含汇总指标及按职工/部门/科目类别的聚合，供大屏直接消费
-- 依赖: biz_dwd_aux_balance_personal

SELECT
    -- KPI指标
    SUM(balance)                                                     AS net_balance,
    SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END)              AS debit_total,
    SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END)         AS credit_total,
    COUNT(DISTINCT employee_name)                                    AS employee_count,
    COUNT(DISTINCT subject_code)                                     AS subject_count,

    -- 按职工分布(JSON)
    (SELECT json_agg(t) FROM (
        SELECT employee_name AS label, SUM(balance) AS value
        FROM biz_dwd_aux_balance_personal
        GROUP BY employee_name
        ORDER BY value DESC
    ) t)                                                             AS by_employee,

    -- 按部门分布(JSON)
    (SELECT json_agg(t) FROM (
        SELECT employee_dept AS label, SUM(ABS(balance)) AS value
        FROM biz_dwd_aux_balance_personal
        GROUP BY employee_dept
        ORDER BY value DESC
    ) t)                                                             AS by_dept,

    -- 按科目类别分布(JSON)
    (SELECT json_agg(t) FROM (
        SELECT subject_category AS label, SUM(ABS(balance)) AS value
        FROM biz_dwd_aux_balance_personal
        GROUP BY subject_category
        ORDER BY value DESC
    ) t)                                                             AS by_category,

    -- 借款余额TOP8(JSON)
    (SELECT json_agg(t) FROM (
        SELECT employee_name AS label, employee_dept, SUM(balance) AS value
        FROM biz_dwd_aux_balance_personal
        GROUP BY employee_name, employee_dept
        HAVING SUM(balance) > 0
        ORDER BY value DESC
        LIMIT 8
    ) t)                                                             AS top8_borrowers

FROM biz_dwd_aux_balance_personal
