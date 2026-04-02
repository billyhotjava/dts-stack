-- ============================================================
-- 卡片名称：经费结构分布（堆叠柱状图）
-- 用途：按项目展示直接成本支出、间接费用支出、剩余经费的结构
-- 所属大屏：F1 项目经费大屏
-- 数据表：biz_dwd_project_fund（DWD 层）
-- ============================================================

SELECT
    project_id,                                                   -- 项目编号
    direct_spent,                                                 -- 直接成本支出（万元，蓝色段）
    indirect_spent,                                               -- 间接费用支出（万元，紫色段）
    GREATEST(total_fund - direct_spent - indirect_spent, 0)       -- 剩余经费（万元，浅灰段）
        AS remaining
FROM biz_dwd_project_fund
ORDER BY total_fund DESC;
