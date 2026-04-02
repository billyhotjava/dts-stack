-- ================================================================
-- 查询卡片: 风险域周期汇总
-- 用途: 按月汇总风险趋势，含新增数、释放数、累计数
-- 对应大屏: Screen 9（风险趋势图）
-- 数据表: biz_dws_risk_period_summary
-- ================================================================

SELECT
    period_year,                            -- 统计年度
    period_month,                           -- 统计月份
    project_no,                             -- 项目编号
    dept,                                   -- 责任单位
    total_risk_cnt,                         -- 风险总数
    high_cnt,                               -- 高风险数
    mid_cnt,                                -- 中风险数
    low_cnt,                                -- 低风险数
    released_cnt,                           -- 已释放数
    open_cnt,                               -- 未释放数
    release_rate                            -- 释放率
FROM biz_dws_risk_period_summary
ORDER BY period_year, period_month;
