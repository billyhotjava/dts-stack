-- ============================================================
-- 财务测试数据加载脚本（v2.2.3 更新版）
-- 使用: psql -d <db> -f load_test_data.sql
-- 前提: 已执行 ods_ddl/create_finance_tables.sql 建表
-- 数据覆盖: 2025 / 2026 两年业务
-- 表结构变更:
--   5.1 年度基金表 ─ 宽表改长表（年度×基金来源×基金类别）
--   5.2 项目经费表 ─ 新增 行号/是否重大项目/研究室/项目状态/
--                   直接成本账面支出总额/已收款/待收经费
--   5.3 / 5.4 辅助余额表 ─ 字段不变
-- ============================================================

\set ON_ERROR_STOP on
\set data_dir `pwd`

-- 清空既有数据（仅测试库使用）
TRUNCATE TABLE ods_finance_own_fund;
TRUNCATE TABLE ods_finance_project_fund;
TRUNCATE TABLE ods_finance_aux_balance;
TRUNCATE TABLE ods_finance_aux_balance_personal;

-- 5.1 年度基金表（每年 × 3 基金来源 × 3 基金类别 = 9 行/年）
\copy ods_finance_own_fund(year_num, fund_source, fund_category, amount, note) FROM :'data_dir'/ods_finance_own_fund.csv WITH (FORMAT csv, HEADER true, NULL '');

-- 5.2 项目经费表（8 个项目，研制周期跨 2025-2026 / 2026-2027）
\copy ods_finance_project_fund(row_no, project_id, cycle, total_fund, is_major_project, research_dept, project_status, direct_ctrl, reserve_indirect, direct_spent, direct_rate, indirect_spent, received_fund, receivable_fund) FROM :'data_dir'/ods_finance_project_fund.csv WITH (FORMAT csv, HEADER true, NULL '');

-- 5.3 辅助余额表-合同（按科目+部门+合同维度）
\copy ods_finance_aux_balance(subject_code, subject_name, dept_name, contract_name, balance) FROM :'data_dir'/ods_finance_aux_balance.csv WITH (FORMAT csv, HEADER true, NULL '');

-- 5.4 辅助余额表-个人维度（按科目+员工维度）
\copy ods_finance_aux_balance_personal(subject_code, subject_name, employee_dept, employee_name, balance) FROM :'data_dir'/ods_finance_aux_balance_personal.csv WITH (FORMAT csv, HEADER true, NULL '');

-- 校验
SELECT 'ods_finance_own_fund' AS tbl, count(*) AS rows FROM ods_finance_own_fund
UNION ALL SELECT 'ods_finance_project_fund', count(*) FROM ods_finance_project_fund
UNION ALL SELECT 'ods_finance_aux_balance', count(*) FROM ods_finance_aux_balance
UNION ALL SELECT 'ods_finance_aux_balance_personal', count(*) FROM ods_finance_aux_balance_personal
ORDER BY tbl;

-- 5.1 维度完整性校验（每年应有 3×3=9 行）
SELECT year_num, fund_source, count(*) AS categories
FROM ods_finance_own_fund
GROUP BY year_num, fund_source
ORDER BY year_num, fund_source;
