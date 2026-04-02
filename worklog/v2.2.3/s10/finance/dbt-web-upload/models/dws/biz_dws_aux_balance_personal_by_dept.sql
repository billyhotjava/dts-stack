-- biz_dws_aux_balance_personal_by_dept.sql
-- DWS层：个人辅助余额按部门汇总，包含净余额、借方合计、贷方合计、职工数
-- 依赖: biz_dwd_aux_balance_personal

SELECT
    employee_dept,
    SUM(balance)                                                     AS net_balance,
    SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END)              AS debit_total,
    SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END)         AS credit_total,
    SUM(ABS(balance))                                                AS abs_total,
    COUNT(DISTINCT employee_name)                                    AS employee_count,
    COUNT(DISTINCT subject_code)                                     AS subject_count
FROM biz_dwd_aux_balance_personal
GROUP BY employee_dept
ORDER BY abs_total DESC
