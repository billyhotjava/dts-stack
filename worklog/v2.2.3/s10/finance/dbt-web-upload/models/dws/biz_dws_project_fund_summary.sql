-- biz_dws_project_fund_summary.sql
-- DWS层：项目经费汇总KPI，聚合全部项目的经费指标
-- 依赖: biz_dwd_project_fund

SELECT
    COUNT(*)                                                         AS project_count,
    SUM(total_fund)                                                  AS sum_total_fund,
    SUM(direct_ctrl)                                                 AS sum_direct_ctrl,
    SUM(direct_spent)                                                AS sum_direct_spent,
    SUM(indirect_spent)                                              AS sum_indirect_spent,
    SUM(total_spent)                                                 AS sum_total_spent,
    SUM(reserve_indirect)                                            AS sum_reserve_indirect,

    -- 整体直接成本执行率
    ROUND(SUM(direct_spent) * 100.0
          / NULLIF(SUM(direct_ctrl), 0), 1)                          AS overall_direct_rate,

    -- 整体总经费执行率
    ROUND(SUM(total_spent) * 100.0
          / NULLIF(SUM(total_fund), 0), 1)                           AS overall_total_rate,

    -- 整体间接费用执行率
    ROUND(SUM(indirect_spent) * 100.0
          / NULLIF(SUM(reserve_indirect), 0), 1)                     AS overall_indirect_rate

FROM biz_dwd_project_fund
