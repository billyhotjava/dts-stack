-- biz_dws_aux_balance_by_dept.sql
-- DWS层：辅助余额按部门汇总，包含余额合计、科目数、合同数
-- 依赖: biz_dwd_aux_balance

SELECT
    dept_name,
    SUM(balance)                                                     AS dept_balance,
    COUNT(DISTINCT subject_code)                                     AS subject_count,
    COUNT(DISTINCT CASE
        WHEN contract_name IS NOT NULL AND contract_name != '—'
        THEN contract_name
    END)                                                             AS contract_count,
    COUNT(*)                                                         AS record_count
FROM biz_dwd_aux_balance
GROUP BY dept_name
ORDER BY dept_balance DESC
