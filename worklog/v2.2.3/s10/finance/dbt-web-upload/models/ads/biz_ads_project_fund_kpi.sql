-- biz_ads_project_fund_kpi.sql
-- ADS层：项目经费最终KPI，包含汇总指标及各项目经费结构明细，供大屏直接消费
-- 依赖: biz_dwd_project_fund, biz_dws_project_fund_summary

SELECT
    s.project_count,
    s.sum_total_fund,
    s.sum_direct_ctrl,
    s.sum_direct_spent,
    s.sum_indirect_spent,
    s.sum_total_spent,
    s.overall_direct_rate,
    s.overall_total_rate,
    s.overall_indirect_rate,

    -- 经费结构明细(JSON)：各项目的直接支出、间接支出、剩余经费
    (SELECT json_agg(row_order)
     FROM (
         SELECT
             project_id,
             total_fund,
             direct_spent,
             indirect_spent,
             GREATEST(total_fund - direct_spent - indirect_spent, 0) AS remaining,
             total_rate,
             direct_rate
         FROM biz_dwd_project_fund
         ORDER BY total_fund DESC
     ) row_order
    ) AS project_detail

FROM biz_dws_project_fund_summary s
