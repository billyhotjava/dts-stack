-- ============================================================
-- 卡片名称：合同金额 TOP 8（排行榜）
-- 用途：展示余额最高的前 8 个合同，用于排行榜组件
-- 所属大屏：F2 辅助余额大屏
-- 数据表：biz_dwd_aux_balance（DWD 层）
-- ============================================================

SELECT
    contract_name               -- 合同名称
        AS contract,
    dept_name                   -- 所属部门
        AS dept,
    SUM(balance)                -- 该合同余额合计（元）
        AS balance
FROM biz_dwd_aux_balance
WHERE has_contract = TRUE       -- 仅取有效合同（排除 NULL 和 '—'）
GROUP BY contract_name, dept_name
ORDER BY balance DESC
LIMIT 8;
