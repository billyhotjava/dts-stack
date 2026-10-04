-- Explicit rollback for the PJM v4 MySQL test-source schema.
--
-- This removes only the ten PJM test tables and the temporary verification
-- procedure. It intentionally leaves the dts_pjm_test database itself in
-- place so an operator cannot accidentally remove unrelated test objects.

USE dts_pjm_test;

DROP PROCEDURE IF EXISTS verify_pjm_golden_seed;

DROP TABLE IF EXISTS ods_budget_v2;
DROP TABLE IF EXISTS ods_material_info_v2;
DROP TABLE IF EXISTS ods_risk_measure_v2;
DROP TABLE IF EXISTS ods_risk_info_v2;
DROP TABLE IF EXISTS ods_tech_state_measure_v2;
DROP TABLE IF EXISTS ods_tech_state_v2;
DROP TABLE IF EXISTS ods_quality_measure_v2;
DROP TABLE IF EXISTS ods_quality_issue_v2;
DROP TABLE IF EXISTS ods_progress_measure_v2;
DROP TABLE IF EXISTS ods_project_subject_domain_v2;
