{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi']) }}

-- ============================================================
-- 项目经费 ADS 层：大屏 KPI 指标
-- 输入：biz_dws_project_fund_summary（DWS 层项目经费汇总）
-- 输出：全局经费 KPI，供大屏指标卡片和仪表盘使用
--
-- KPI 说明：
--   sum_total_fund      — 总经费合计
--   sum_direct_ctrl     — 直接成本控制数合计
--   sum_direct_spent    — 直接成本已支出合计（派生）
--   overall_direct_rate — 整体直接成本执行率
--   overall_total_rate  — 整体总经费执行率
-- ============================================================

SELECT
    project_count,

    -- 经费总量 KPI
    sum_total_fund,
    sum_direct_ctrl,
    sum_reserve_indirect,
    sum_direct_spent,
    sum_indirect_spent,
    sum_total_spent,
    sum_remaining_fund,

    -- 执行率 KPI（整体加权，非简单平均）
    overall_direct_rate,
    overall_total_rate,
    overall_indirect_rate

FROM {{ ref('biz_dws_project_fund_summary') }}
