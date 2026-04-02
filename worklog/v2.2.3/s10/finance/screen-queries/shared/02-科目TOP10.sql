-- ============================================================
-- 卡片名称：按科目分布 TOP 10（横向条形图）
-- 用途：展示余额最高的前 10 个科目
-- 所属大屏：F2 辅助余额大屏
-- 数据表：biz_dwd_aux_balance（DWD 层）
-- ============================================================

SELECT
    subject_code || ' ' || subject_name   -- 科目标签（编号 + 名称，如 "5001.01 原材料-钢材"）
        AS label,
    SUM(balance)                          -- 该科目余额合计（元）
        AS value
FROM biz_dwd_aux_balance
GROUP BY subject_code, subject_name       -- 按科目编号和名称聚合
ORDER BY value DESC
LIMIT 10;
