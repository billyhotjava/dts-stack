{{ config(materialized='table', tags=['finance', 'biz', 'dwd']) }}

-- ============================================================
-- 项目经费 DWD 层：计算派生字段（直接成本支出、总支出、总执行率等）
-- 输入：fin_ods.project_fund（原始项目经费表）
-- 输出：含全部派生指标的项目经费明细
-- ============================================================

SELECT
    -- === 原始字段 ===
    project_id,
    cycle,
    total_fund,
    direct_ctrl,
    reserve_indirect,
    direct_rate,
    indirect_spent,

    -- === 派生字段 ===

    -- 直接成本支出 = 直接成本控制数 × 直接成本执行率 / 100
    ROUND(direct_ctrl * direct_rate / 100, 2) AS direct_spent,

    -- 总支出 = 直接成本支出 + 间接费用支出
    ROUND(direct_ctrl * direct_rate / 100, 2) + indirect_spent AS total_spent,

    -- 总经费执行率(%) = 总支出 / 总经费 × 100
    CASE
        WHEN total_fund > 0
        THEN ROUND(
            (ROUND(direct_ctrl * direct_rate / 100, 2) + indirect_spent)
            * 100.0 / total_fund, 1)
        ELSE 0
    END AS total_rate,

    -- 间接费用执行率(%) = 间接费用支出 / 预留间接费用 × 100
    CASE
        WHEN reserve_indirect > 0
        THEN ROUND(indirect_spent * 100.0 / reserve_indirect, 1)
        ELSE 0
    END AS indirect_rate

FROM {{ source('fin_ods', 'project_fund') }}
WHERE project_id IS NOT NULL
