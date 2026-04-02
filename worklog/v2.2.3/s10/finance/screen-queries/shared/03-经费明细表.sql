-- ============================================================
-- 卡片名称：项目经费明细表
-- 用途：展示每个项目的经费全量明细，含原始字段和派生字段
-- 所属大屏：F1 项目经费大屏
-- 数据表：biz_dwd_project_fund（DWD 层）
-- ============================================================

SELECT
    project_id,                 -- 项目编号
    cycle,                      -- 研制周期（如 2026.01-2027.06）
    total_fund,                 -- 总经费（万元）
    direct_ctrl,                -- 直接成本控制数（万元）
    reserve_indirect,           -- 预留间接费用和收益（万元）
    direct_rate,                -- 直接成本执行率（%）
    indirect_spent,             -- 间接费用支出和收益总额（万元）
    direct_spent,               -- 直接成本支出（万元，派生）
    total_spent,                -- 总支出（万元，派生）
    total_rate,                 -- 总经费执行率（%，派生）
    indirect_rate               -- 间接费用执行率（%，派生）
FROM biz_dwd_project_fund
ORDER BY project_id;
