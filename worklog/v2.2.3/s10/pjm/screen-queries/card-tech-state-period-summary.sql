-- ================================================================
-- 查询卡片: 技术状态域周期汇总
-- 用途: 按月汇总技术状态变更趋势，含新增数、完成数、累计数
-- 对应大屏: Screen 6（技术状态趋势图）
-- 数据表: biz_dws_tech_state_period_summary
-- ================================================================

SELECT
    period_year,                            -- 统计年度
    period_month,                           -- 统计月份
    project_no,                             -- 项目编号
    dept,                                   -- 责任单位
    total_change_cnt,                       -- 变更总数
    cat_i,                                  -- I类变更数
    cat_ii,                                 -- II类变更数
    cat_iii,                                -- III类变更数
    signature_completed_cnt,                -- 签署完成数
    file_signed_cnt,                        -- 文件签署数
    reform_done_cnt,                        -- 整改完成数
    reform_pending_cnt                      -- 整改待完成数
FROM biz_dws_tech_state_period_summary
ORDER BY period_year, period_month;
