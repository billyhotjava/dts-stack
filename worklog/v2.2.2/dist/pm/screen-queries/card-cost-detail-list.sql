-- ================================================================
-- 查询卡片: 成本核算明细
-- 用途: 成本核算明细列表，展示各项目各期间的预算与实际金额
-- 对应大屏: Screen 5（成本看板）
-- 数据表: biz_dwd_cost_accounting
-- ================================================================

SELECT
    cost_id,
    project_no,
    project_name,
    dept,
    cost_category,
    remark,
    budget_amount,
    actual_amount,
    deviation_amount,
    execution_rate,
    deviation_rate,
    accounting_period,
    period_year,
    period_month,
    source_table,
    etl_time
FROM biz_dwd_cost_accounting
ORDER BY accounting_period DESC, project_no
