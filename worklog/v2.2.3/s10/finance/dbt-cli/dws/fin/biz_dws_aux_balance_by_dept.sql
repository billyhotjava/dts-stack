{{ config(materialized='table', tags=['finance', 'biz', 'dws']) }}

-- ============================================================
-- 辅助余额 DWS 层：按部门汇总
-- 输入：biz_dwd_aux_balance（DWD 层含费用分类的辅助余额）
-- 输出：每个部门的余额合计、科目数、合同数
-- ============================================================

SELECT
    dept_name,

    -- 余额合计
    SUM(balance)                         AS dept_balance,

    -- 涉及科目数（去重）
    COUNT(DISTINCT subject_code)         AS subject_count,

    -- 有效合同数（去重，排除无合同记录）
    COUNT(DISTINCT CASE
        WHEN has_contract THEN contract_name
    END)                                 AS contract_count,

    -- 记录数
    COUNT(*)                             AS record_count

FROM {{ ref('biz_dwd_aux_balance') }}
GROUP BY dept_name
ORDER BY dept_balance DESC
