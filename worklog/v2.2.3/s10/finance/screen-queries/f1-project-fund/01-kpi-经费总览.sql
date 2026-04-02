-- ============================================================
-- 卡片名称：KPI 经费总览
-- 用途：项目经费大屏顶部 KPI 指标卡片（5个核心指标 + 辅助指标）
-- 所属大屏：F1 项目经费大屏
-- 数据表：biz_ads_project_fund_kpi（ADS 层）
-- ============================================================

SELECT
    project_count,              -- 项目数量
    sum_total_fund,             -- 总经费合计（万元）
    sum_direct_ctrl,            -- 直接成本控制数合计（万元）
    sum_reserve_indirect,       -- 预留间接费用和收益合计（万元）
    sum_direct_spent,           -- 直接成本已支出合计（万元，派生）
    sum_indirect_spent,         -- 间接费用支出合计（万元）
    sum_total_spent,            -- 总支出合计（万元）
    sum_remaining_fund,         -- 剩余经费合计（万元）
    overall_direct_rate,        -- 整体直接成本执行率（%，加权计算）
    ROUND(overall_direct_rate, 2) || '%' AS overall_direct_rate_pct,        -- [显示用] 整体直接成本执行率（%，加权计算）
    overall_total_rate,         -- 整体总经费执行率（%，加权计算）
    ROUND(overall_total_rate, 2) || '%' AS overall_total_rate_pct,         -- [显示用] 整体总经费执行率（%，加权计算）
    overall_indirect_rate,  -- 整体间接费用执行率（%，加权计算）
    ROUND(overall_indirect_rate, 2) || '%' AS overall_indirect_rate_pct       -- [显示用] 整体间接费用执行率（%，加权计算）
FROM biz_ads_project_fund_kpi;
