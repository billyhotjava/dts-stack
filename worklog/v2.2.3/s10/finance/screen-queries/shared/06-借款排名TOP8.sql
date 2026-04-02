-- ============================================================
-- 卡片名称：借款余额 TOP 8（排行榜）
-- 用途：展示借方净余额最高的前 8 名职工（公司的净债务人）
-- 所属大屏：F3 个人辅助余额大屏
-- 数据表：biz_dwd_aux_balance_personal（DWD 层）
-- ============================================================

SELECT
    employee_name               -- 职工名称
        AS label,
    employee_dept,              -- 职工部门
    SUM(balance)                -- 该职工净余额（元，仅取正值即借方净余额）
        AS value
FROM biz_dwd_aux_balance_personal
GROUP BY employee_name, employee_dept
HAVING SUM(balance) > 0         -- 仅取借方净余额为正的职工（即公司的净债务人）
ORDER BY value DESC
LIMIT 8;
