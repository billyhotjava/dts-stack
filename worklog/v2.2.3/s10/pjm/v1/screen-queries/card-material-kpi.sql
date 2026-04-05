-- ================================================================
-- 查询卡片: 物资域KPI总览
-- 用途: 按项目统计物资KPI，包括物资总数、长周期占比、延迟率等
-- 对应大屏: Screen 10（物资总览）
-- 数据表: biz_ads_material_kpi
-- ================================================================

SELECT
    project_no,                             -- 项目编号
    total_cnt,                              -- 物资总数
    long_cycle_cnt,                         -- 长周期物资数
    long_cycle_rate,                        -- 长周期占比
    ROUND(long_cycle_rate * 100, 2) || '%' AS long_cycle_rate_pct,                        -- [显示用] 长周期占比
    self_developed_cnt,                     -- 自研物资数
    outsource_cnt,                          -- 外协物资数
    outsource_rate,                         -- 外协占比
    ROUND(outsource_rate * 100, 2) || '%' AS outsource_rate_pct,                         -- [显示用] 外协占比
    has_risk_cnt,                           -- 有风险物资数
    supplier_count                          -- 供应商数量
FROM biz_ads_material_kpi
ORDER BY project_no;
