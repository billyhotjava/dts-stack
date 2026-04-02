{{ config(materialized='table', tags=['finance', 'biz', 'dws']) }}

-- ============================================================
-- 个人辅助余额 DWS 层：按部门汇总
-- 输入：biz_dwd_aux_balance_personal（DWD 层含借贷方向的个人余额）
-- 输出：每个部门的净余额、借方合计、贷方合计、职工数
-- ============================================================

SELECT
    employee_dept,

    -- 净余额（借方 - 贷方）
    SUM(balance)                                              AS net_balance,

    -- 借方合计（正数余额之和，代表应收/借款）
    SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END)        AS debit_total,

    -- 贷方合计（负数余额绝对值之和，代表应付/代扣）
    SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END)   AS credit_total,

    -- 资金往来规模（绝对值之和）
    SUM(abs_balance)                                           AS abs_total,

    -- 涉及职工数（去重）
    COUNT(DISTINCT employee_name)                              AS employee_count,

    -- 涉及科目数（去重）
    COUNT(DISTINCT subject_code)                               AS subject_count,

    -- 记录数
    COUNT(*)                                                   AS record_count

FROM {{ ref('biz_dwd_aux_balance_personal') }}
GROUP BY employee_dept
ORDER BY abs_total DESC
