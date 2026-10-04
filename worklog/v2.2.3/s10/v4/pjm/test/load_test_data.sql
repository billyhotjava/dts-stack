-- ============================================================
-- 项目管理 v2 测试数据加载脚本
--
-- 用法（二选一）：
--
-- 1) 本地主机 psql：
--    cd worklog/v2.2.3/s10/v4/pjm/test
--    psql -h <host> -U <user> -d <db> -v ON_ERROR_STOP=1 -f load_test_data.sql
--
-- 2) 容器内 psql（文件已 docker cp 到容器 /tmp/pjm-test/）：
--    docker exec <pg-container> bash -c \
--      "cd /tmp/pjm-test && psql -U <user> -d <db> -v ON_ERROR_STOP=1 -f load_test_data.sql"
--
-- 前提：
--   1. 已执行 ods_ddl/ods_create_tables_v2.sql 建表
--   2. psql 进程当前工作目录即本文件所在目录（CSV 相对路径依赖于此）
--
-- 数据覆盖：10 个项目、10 个科室，时间跨度 2026-2027
--   XM-2026-C01 / C02 / C03 / C04 / C05
--   XM-2026-C06 / C07 / C08 / C09 / C10
-- ============================================================

\set ON_ERROR_STOP on

-- 清空既有数据（仅测试库使用）
TRUNCATE TABLE ods_project_subject_domain_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_progress_measure_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_quality_issue_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_quality_measure_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_tech_state_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_tech_state_measure_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_risk_info_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_risk_measure_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_material_info_v2 RESTART IDENTITY CASCADE;
TRUNCATE TABLE ods_budget_v2 RESTART IDENTITY CASCADE;

-- 1. 进度信息汇总（DWD 核心事实）
\copy ods_project_subject_domain_v2(project_no, subsystem, node_task, plan_start_date, plan_date, plan_week, deliverable, node_type, owner, dept, dept_leader, completion_status, collab_dept, supervisor_dept, delay_expected_date, incomplete_reason, risk_level, risk_content, delay_impact, actual_start_date, actual_date, actual_week, institute_leader, source, original_plan_date, delay_days_changed, delay_days_unchanged, delay_applied, project_manager, last_update_time, last_update_week, filled_by, highlight) FROM 'ods_project_subject_domain_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 2. 进度跟进措施
\copy ods_progress_measure_v2(project_no, subsystem, node_task, plan_date, plan_week, completion_status, measure_category, measure_title, follow_up_person, main_recipient, cc_recipient, follow_up_date, follow_up_week, closure_status, final_closure_date, final_closure_week, closure_deliverable_type, closure_deliverable, risk_content, last_update_time, last_update_week, remark, filled_by) FROM 'ods_progress_measure_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 3. 质量信息汇总（DWD 核心事实）
\copy ods_quality_issue_v2(project_no, subsystem, issue_name, dept, team_leader, dept_leader, issue_date, issue_week, issue_summary, issue_category, zero_plan, zero_plan_synced, new_plan_count, status, current_progress, zero_complete_date, zero_complete_week, last_update_time, last_update_week, project_manager, filled_by) FROM 'ods_quality_issue_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 4. 质量跟进措施
\copy ods_quality_measure_v2(project_no, subsystem, issue_name, dept, team_leader, dept_leader, issue_date, issue_week, issue_summary, issue_category, zero_plan, zero_plan_synced, new_plan_count, status, current_progress, project_manager, measure_category, measure_title, follow_up_person, main_recipient, cc_recipient, follow_up_date, follow_up_week, closure_status, final_closure_date, final_closure_week, closure_deliverable_type, closure_deliverable, risk_content, last_update_time, last_update_week, remark, filled_by) FROM 'ods_quality_measure_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 5. 技术状态信息汇总（DWD 核心事实）
\copy ods_tech_state_v2(project_no, tech_state_name, change_item, owner, dept, dept_leader, change_submit_time, change_submit_week, completion_signature, signature_closure_date, signature_closure_week, change_reason, change_category, plan_file_closure_date, plan_file_closure_week, plan_reform_date, plan_reform_week, plan_synced, new_plan_count, review_situation, affected_files, affected_objects, file_signature_status, file_signature_date, file_signature_week, reform_status, reform_date, reform_week, project_manager, last_update_time, last_update_week, filled_by, remark) FROM 'ods_tech_state_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 6. 技术状态跟进措施
\copy ods_tech_state_measure_v2(project_no, tech_state_name, change_item, owner, dept, dept_leader, change_submit_time, change_submit_week, completion_signature, signature_closure_date, signature_closure_week, change_reason, change_category, plan_file_closure_date, plan_file_closure_week, plan_reform_date, plan_reform_week, plan_synced, new_plan_count, affected_files, affected_objects, file_signature_status, reform_status, project_manager, measure_category, measure_title, follow_up_person, main_recipient, cc_recipient, follow_up_date, follow_up_week, closure_status, final_closure_date, final_closure_week, closure_deliverable_type, closure_deliverable, risk_content, last_update_time, last_update_week, remark, filled_by) FROM 'ods_tech_state_measure_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 7. 风险信息汇总（DWD 核心事实）
\copy ods_risk_info_v2(project_no, risk_name, subsystem, belonging_unit, risk_description, risk_submit_time, risk_submit_week, risk_phase, risk_category, risk_level, impact_scope, response_measure, final_release_time, final_release_week, monthly_control_plan, weekly_release_plan, release_plan_synced, new_plan_count, progress_stat_time, progress_stat_week, progress_situation, response_owner, control_owner, dept, risk_status, risk_release_date, risk_release_week, remark, last_update_time, last_update_week, filled_by) FROM 'ods_risk_info_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 8. 风险跟进措施
\copy ods_risk_measure_v2(project_no, risk_name, subsystem, belonging_unit, risk_description, risk_submit_time, risk_submit_week, risk_phase, risk_category, risk_level, impact_scope, response_measure, final_release_time, monthly_control_plan, weekly_release_plan, release_plan_synced, new_plan_count, progress_stat_time, progress_stat_week, progress_situation, response_owner, control_owner, dept, risk_status, project_manager, measure_category, measure_title, follow_up_person, main_recipient, cc_recipient, follow_up_date, follow_up_week, closure_status, final_closure_date, final_closure_week, closure_deliverable_type, closure_deliverable, risk_content, last_update_time, last_update_week, remark, filled_by) FROM 'ods_risk_measure_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 9. 重要物料信息
\copy ods_material_info_v2(project_no, subsystem, pbs_no, pbs_name, self_or_outsource, supplier_name, is_long_cycle, contract_negotiation_date, contract_negotiation_week, contract_delivery_date, contract_delivery_week, actual_delivery_date, actual_delivery_week, plan_inspect_date, plan_inspect_week, complete_inspect_date, complete_inspect_week, install_date, install_week, dept_owner, control_dept_owner, weekly_progress, affects_major_node, risk_level, risk_content, delay_impact, last_update_time, last_update_week, remark) FROM 'ods_material_info_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 10. 预算执行台账（DWD 三本账快照事实，金额单位万元）
\copy ods_budget_v2(project_no, budget_no, subtopic, research_lab, budget_amount_adjusted, prepaid_amount, book_cost_amount, payable_amount, snapshot_date) FROM 'ods_budget_v2.csv' WITH (FORMAT csv, HEADER true, NULL '')

-- 校验
SELECT 'ods_project_subject_domain_v2' AS tbl, count(*) AS rows, count(DISTINCT project_no) AS projects FROM ods_project_subject_domain_v2
UNION ALL SELECT 'ods_progress_measure_v2', count(*), count(DISTINCT project_no) FROM ods_progress_measure_v2
UNION ALL SELECT 'ods_quality_issue_v2', count(*), count(DISTINCT project_no) FROM ods_quality_issue_v2
UNION ALL SELECT 'ods_quality_measure_v2', count(*), count(DISTINCT project_no) FROM ods_quality_measure_v2
UNION ALL SELECT 'ods_tech_state_v2', count(*), count(DISTINCT project_no) FROM ods_tech_state_v2
UNION ALL SELECT 'ods_tech_state_measure_v2', count(*), count(DISTINCT project_no) FROM ods_tech_state_measure_v2
UNION ALL SELECT 'ods_risk_info_v2', count(*), count(DISTINCT project_no) FROM ods_risk_info_v2
UNION ALL SELECT 'ods_risk_measure_v2', count(*), count(DISTINCT project_no) FROM ods_risk_measure_v2
UNION ALL SELECT 'ods_material_info_v2', count(*), count(DISTINCT project_no) FROM ods_material_info_v2
UNION ALL SELECT 'ods_budget_v2', count(*), count(DISTINCT project_no) FROM ods_budget_v2
ORDER BY tbl;
