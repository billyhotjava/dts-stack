{{ config(materialized='table', tags=['finance', 'biz', 'dws']) }}

-- ============================================================
-- 项目经费 DWS 层：汇总全部项目的经费 KPI
-- 输入：biz_dwd_project_fund（DWD 层含派生字段的项目经费）
-- 输出：项目级汇总指标 + 全局汇总行
-- ============================================================

WITH project_level AS (
    -- 项目级明细（保留供下游使用）
    SELECT
        project_id,
        cycle,
        total_fund,
        direct_ctrl,
        reserve_indirect,
        direct_rate,
        indirect_spent,
        direct_spent,
        total_spent,
        total_rate,
        indirect_rate,

        -- 剩余经费 = 总经费 - 总支出
        GREATEST(total_fund - total_spent, 0) AS remaining_fund

    FROM {{ ref('biz_dwd_project_fund') }}
),

global_summary AS (
    -- 全局汇总
    SELECT
        COUNT(*)                     AS project_count,
        SUM(total_fund)              AS sum_total_fund,
        SUM(direct_ctrl)             AS sum_direct_ctrl,
        SUM(reserve_indirect)        AS sum_reserve_indirect,
        SUM(direct_spent)            AS sum_direct_spent,
        SUM(indirect_spent)          AS sum_indirect_spent,
        SUM(total_spent)             AS sum_total_spent,
        SUM(remaining_fund)          AS sum_remaining_fund,

        -- 整体直接成本执行率 = SUM(直接支出) / SUM(直接控制数)
        ROUND(SUM(direct_spent) * 100.0
              / NULLIF(SUM(direct_ctrl), 0), 1) AS overall_direct_rate,

        -- 整体总经费执行率 = SUM(总支出) / SUM(总经费)
        ROUND(SUM(total_spent) * 100.0
              / NULLIF(SUM(total_fund), 0), 1)  AS overall_total_rate,

        -- 整体间接费用执行率 = SUM(间接支出) / SUM(预留间接)
        ROUND(SUM(indirect_spent) * 100.0
              / NULLIF(SUM(reserve_indirect), 0), 1) AS overall_indirect_rate

    FROM project_level
)

SELECT * FROM global_summary
