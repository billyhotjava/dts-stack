{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi']) }}

-- ============================================================
-- 辅助余额 ADS 层：大屏 KPI 指标
-- 输入：biz_dwd_aux_balance（DWD 层含费用分类的辅助余额）
-- 输出：4 个 KPI 指标，供大屏指标卡片使用
--
-- KPI 说明：
--   total_balance  — 余额合计（所有科目余额之和）
--   subject_count  — 科目数量（去重）
--   contract_count — 合同数量（去重，排除无效合同）
--   dept_count     — 部门数量（去重）
-- ============================================================

SELECT
    -- 余额合计（单位：元，前端转换为万元显示）
    SUM(balance)                          AS total_balance,

    -- 涉及科目数（去重）
    COUNT(DISTINCT subject_code)          AS subject_count,

    -- 有效合同数（去重，排除 NULL 和占位符 '—'）
    COUNT(DISTINCT CASE
        WHEN has_contract THEN contract_name
    END)                                  AS contract_count,

    -- 涉及部门数（去重）
    COUNT(DISTINCT dept_name)             AS dept_count

FROM {{ ref('biz_dwd_aux_balance') }}
