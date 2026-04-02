{{ config(materialized='table', tags=['finance', 'biz', 'dwd']) }}

-- ============================================================
-- 辅助余额 DWD 层：根据科目编号前缀添加费用类别字段
-- 输入：fin_ods.ods_finance_aux_balance（原始辅助余额表）
-- 输出：含费用分类标签的辅助余额明细
-- ============================================================

SELECT
    -- === 原始字段 ===
    subject_code,
    subject_name,
    dept_name,
    contract_name,
    balance,

    -- === 派生字段：根据科目编号前缀推导费用类别 ===
    CASE
        WHEN subject_code LIKE '5001%' THEN '原材料/设备'
        WHEN subject_code LIKE '5101%' THEN '外协/服务'
        WHEN subject_code LIKE '5201%' THEN '折旧'
        WHEN subject_code LIKE '5301%' THEN '检测试验'
        WHEN subject_code LIKE '5401%' THEN '设计咨询'
        WHEN subject_code LIKE '5501%' THEN '租赁'
        WHEN subject_code LIKE '5601%' THEN '培训'
        ELSE '其他'
    END AS expense_category,

    -- === 合同有效性标记（排除 NULL 和占位符 '—'） ===
    CASE
        WHEN contract_name IS NOT NULL AND contract_name != '—'
        THEN TRUE
        ELSE FALSE
    END AS has_contract

FROM {{ source('fin_ods', 'ods_finance_aux_balance') }}
WHERE subject_code IS NOT NULL
