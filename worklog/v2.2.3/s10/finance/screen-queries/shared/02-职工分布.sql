-- ============================================================
-- 卡片名称：按职工分布（横向条形图）
-- 用途：按职工汇总净余额，正数（借方）蓝色、负数（贷方）绿色
-- 所属大屏：F3 个人辅助余额大屏
-- 数据表：biz_dwd_aux_balance_personal（DWD 层）
-- ============================================================

SELECT
    employee_name               -- 职工名称
        AS label,
    SUM(balance)                -- 该职工净余额（正=欠公司，负=公司欠）
        AS value
FROM biz_dwd_aux_balance_personal
GROUP BY employee_name          -- 按职工聚合
ORDER BY value DESC;
