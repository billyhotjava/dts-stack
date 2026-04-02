{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi']) }}

-- ============================================================
-- 个人辅助余额 ADS 层：大屏 KPI 指标
-- 输入：biz_dwd_aux_balance_personal（DWD 层含借贷方向的个人余额）
-- 输出：5 个 KPI 指标，供大屏指标卡片使用
--
-- KPI 说明：
--   net_balance    — 净余额（正=公司净债权，负=公司净债务）
--   debit_total    — 借方合计（应收/借款总额）
--   credit_total   — 贷方合计（应付/代扣总额）
--   employee_count — 涉及职工数
--   subject_count  — 科目数量
-- ============================================================

SELECT
    -- 净余额 = 所有 balance 直接求和
    SUM(balance)                                              AS net_balance,

    -- 借方合计（正数余额之和，代表职工借款/应收）
    SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END)        AS debit_total,

    -- 贷方合计（负数余额绝对值之和，代表应付职工薪酬）
    SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END)   AS credit_total,

    -- 涉及职工数（去重）
    COUNT(DISTINCT employee_name)                              AS employee_count,

    -- 涉及科目数（去重）
    COUNT(DISTINCT subject_code)                               AS subject_count

FROM {{ ref('biz_dwd_aux_balance_personal') }}
