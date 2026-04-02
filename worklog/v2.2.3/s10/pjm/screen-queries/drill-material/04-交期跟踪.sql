-- ================================================================
-- 查询卡片: 物资域周期汇总
-- 用途: 按项目汇总物资统计，含交付完成率、长周期物资数等
-- 对应大屏: Screen 10（物资汇总统计）
-- 数据表: biz_dws_material_period_summary
-- ================================================================

SELECT
    project_no,                             -- 项目编号
    subsystem,                              -- 分系统
    total_cnt,                              -- 物资总数
    long_cycle_cnt,                         -- 长周期物资数
    long_cycle_rate,                        -- 长周期占比
    self_developed_cnt,                     -- 自研物资数
    outsource_cnt,                          -- 外协物资数
    outsource_rate,                         -- 外协占比
    has_risk_cnt,                           -- 有风险物资数
    supplier_count                          -- 供应商数量
FROM biz_dws_material_period_summary
ORDER BY project_no;
