-- ================================================================
-- 查询卡片: 进度措施汇总
-- 用途: 按项目汇总进度措施统计，含措施总数、闭环率等
-- 对应大屏: Screen 12（进度措施汇总）
-- 数据表: biz_dws_progress_measure_summary
-- ================================================================

SELECT
    project_no,                             -- 项目编号
    subsystem,                              -- 分系统
    total_measure_cnt,                      -- 措施总数
    closed_cnt,                             -- 已闭环数
    open_cnt,                               -- 未闭环数
    closure_rate,                           -- 闭环率
    cat_design_cnt,                         -- 设计类措施数
    cat_process_cnt,                        -- 工艺类措施数
    cat_management_cnt,                     -- 管理类措施数
    cat_component_cnt,                      -- 元器件类措施数
    cat_operation_cnt,                      -- 操作类措施数
    cat_outsource_cnt,                      -- 外协类措施数
    cat_software_cnt,                       -- 软件类措施数
    cat_other_cnt                           -- 其他类措施数
FROM biz_dws_progress_measure_summary
ORDER BY project_no;
