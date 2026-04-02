{{ config(materialized='table', tags=['finance', 'biz', 'dwd']) }}

-- ============================================================
-- 个人辅助余额 DWD 层：添加借贷方向和科目类别
-- 输入：fin_ods.ods_finance_aux_balance_personal（原始个人辅助余额表）
-- 输出：含借贷方向标签和科目分类的个人辅助余额明细
-- ============================================================

SELECT
    -- === 原始字段 ===
    subject_code,
    subject_name,
    employee_dept,
    employee_name,
    balance,

    -- === 派生字段：借贷方向 ===
    -- 正数 = 借方（应收/借款），负数 = 贷方（应付/代扣）
    CASE
        WHEN balance > 0 THEN 'debit'
        WHEN balance < 0 THEN 'credit'
        ELSE 'zero'
    END AS balance_direction,

    -- === 派生字段：余额绝对值（方便下游汇总） ===
    ABS(balance) AS abs_balance,

    -- === 派生字段：科目类别 ===
    CASE
        WHEN subject_code LIKE '1122%' THEN '其他应收-借款'
        WHEN subject_code LIKE '2211%' THEN '应付职工薪酬'
        ELSE '其他'
    END AS subject_category

FROM {{ source('fin_ods', 'ods_finance_aux_balance_personal') }}
WHERE subject_code IS NOT NULL
