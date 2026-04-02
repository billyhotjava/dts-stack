-- ============================================================
-- 卡片名称：按部门分布（环形图）
-- 用途：展示各部门的余额占比
-- 所属大屏：F2 辅助余额大屏
-- 数据表：biz_dws_aux_balance_by_dept（DWS 层）
-- ============================================================

SELECT
    dept_name,                  -- 部门名称
    dept_balance,               -- 部门余额合计（元）
    subject_count,              -- 该部门涉及科目数
    contract_count,             -- 该部门有效合同数
    record_count                -- 该部门记录数
FROM biz_dws_aux_balance_by_dept
ORDER BY dept_balance DESC;
