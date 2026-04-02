-- ================================================================
-- 查询卡片: 技术状态域KPI总览
-- 用途: 按月统计技术状态KPI，包括变更数、签署完成率等
-- 对应大屏: Screen 4（技术状态总览）、Screen 6（技术状态看板KPI）
-- 数据表: biz_ads_tech_state_kpi
-- ================================================================

SELECT
    period_year,                            -- 统计年度
    period_month,                           -- 统计月份
    total_change_cnt,                       -- 变更总数
    cat_i,                                  -- I类变更数
    cat_ii,                                 -- II类变更数
    cat_iii,                                -- III类变更数
    signature_completed_cnt,                -- 签署完成数
    file_signed_cnt,                        -- 文件签署数
    reform_done_cnt,                        -- 整改完成数
    reform_pending_cnt                      -- 整改待完成数
FROM biz_ads_tech_state_kpi
ORDER BY period_year, period_month;
