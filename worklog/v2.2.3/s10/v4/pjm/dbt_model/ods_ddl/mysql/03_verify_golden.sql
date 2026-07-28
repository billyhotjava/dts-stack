-- Fail-fast verification for the PJM v4 MySQL source schema and 55-row seed.
-- Run with the mysql CLI after 01_create_ods_source_tables.sql and
-- 02_seed_golden.sql. Any mismatch raises SQLSTATE 45000.

USE dts_pjm_test;

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS verify_pjm_golden_seed;

DELIMITER $$

CREATE PROCEDURE verify_pjm_golden_seed()
BEGIN
  DECLARE v_actual INT DEFAULT 0;
  DECLARE v_message VARCHAR(255);

  SELECT COUNT(*) INTO v_actual
    FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN (
       'ods_project_subject_domain_v2',
       'ods_progress_measure_v2',
       'ods_quality_issue_v2',
       'ods_quality_measure_v2',
       'ods_tech_state_v2',
       'ods_tech_state_measure_v2',
       'ods_risk_info_v2',
       'ods_risk_measure_v2',
       'ods_material_info_v2',
       'ods_budget_v2'
     );
  IF v_actual <> 10 THEN
    SET v_message = CONCAT('expected 10 PJM tables, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name LIKE 'ods\_%\_v2'
     AND column_name REGEXP '^_dts_';
  IF v_actual <> 0 THEN
    SET v_message = CONCAT('raw MySQL source contains ', v_actual, ' reserved _dts_* columns');
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(DISTINCT table_name) INTO v_actual
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND index_name IN (
       'idx_ods_project_subject_domain_v2_project_no',
       'idx_ods_progress_measure_v2_project_no',
       'idx_ods_quality_issue_v2_project_no',
       'idx_ods_quality_measure_v2_project_no',
       'idx_ods_tech_state_v2_project_no',
       'idx_ods_tech_state_measure_v2_project_no',
       'idx_ods_risk_info_v2_project_no',
       'idx_ods_risk_measure_v2_project_no',
       'idx_ods_material_info_v2_project_no',
       'idx_ods_budget_v2_project_no'
     );
  IF v_actual <> 10 THEN
    SET v_message = CONCAT('expected 10 project_no indexes, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'ods_budget_v2'
     AND index_name = 'uk_ods_budget_v2_budget_no'
     AND column_name = 'budget_no'
     AND non_unique = 0;
  IF v_actual <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'missing unique budget_no index';
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_project_subject_domain_v2;
  IF v_actual <> 10 THEN
    SET v_message = CONCAT('project subject domain expected exactly 10 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_progress_measure_v2;
  IF v_actual <> 2 THEN
    SET v_message = CONCAT('progress measures expected exactly 2 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_quality_issue_v2;
  IF v_actual <> 14 THEN
    SET v_message = CONCAT('quality issues expected exactly 14 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_quality_measure_v2;
  IF v_actual <> 2 THEN
    SET v_message = CONCAT('quality measures expected exactly 2 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_tech_state_v2;
  IF v_actual <> 8 THEN
    SET v_message = CONCAT('technical states expected exactly 8 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_tech_state_measure_v2;
  IF v_actual <> 2 THEN
    SET v_message = CONCAT('technical-state measures expected exactly 2 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_risk_info_v2;
  IF v_actual <> 8 THEN
    SET v_message = CONCAT('risks expected exactly 8 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_risk_measure_v2;
  IF v_actual <> 2 THEN
    SET v_message = CONCAT('risk measures expected exactly 2 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_material_info_v2;
  IF v_actual <> 2 THEN
    SET v_message = CONCAT('materials expected exactly 2 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(*) INTO v_actual
    FROM ods_budget_v2;
  IF v_actual <> 5 THEN
    SET v_message = CONCAT('budgets expected exactly 5 rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(DISTINCT completion_status) INTO v_actual
    FROM ods_project_subject_domain_v2
   WHERE id BETWEEN 1001 AND 1010
     AND completion_status IN (
       '正常待完成',
       '按时完成',
       '超期已完成已变更',
       '超期已完成未变更',
       '不正常待变更',
       '超期未完成未变更',
       '超期未完成已变更'
     );
  IF v_actual <> 7 THEN
    SET v_message = CONCAT('expected 7 canonical completion statuses, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT
    (SUM(node_type IN ('一般', '一般节点')) > 0)
    + (SUM(node_type IN ('重要', '重要节点')) > 0)
    + (SUM(node_type IN ('重大', '重大节点')) > 0)
    + (SUM(node_type IN ('里程碑', '里程碑节点')) > 0)
    INTO v_actual
    FROM ods_project_subject_domain_v2
   WHERE id BETWEEN 1001 AND 1010;
  IF v_actual <> 4 THEN
    SET v_message = CONCAT('expected all 4 node-type branches, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(DISTINCT issue_category) INTO v_actual
    FROM ods_quality_issue_v2
   WHERE id BETWEEN 3001 AND 3014
     AND issue_category IN (
       '设计', '工艺', '管理', '元器件', '操作',
       '外协外购', '软件', '环境', '其他'
     );
  IF v_actual <> 9 THEN
    SET v_message = CONCAT('expected 9 quality categories, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT
    (SUM(change_category IN ('I', 'Ⅰ', 'I类', '1', '一')) > 0)
    + (SUM(change_category IN ('II', 'Ⅱ', 'II类', '2', '二')) > 0)
    + (SUM(change_category IN ('III', 'Ⅲ', 'III类', '3', '三')) > 0)
    INTO v_actual
    FROM ods_tech_state_v2
   WHERE id BETWEEN 5001 AND 5008;
  IF v_actual <> 3 THEN
    SET v_message = CONCAT('expected I/II/III technical categories, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(DISTINCT risk_level) INTO v_actual
    FROM ods_risk_info_v2
   WHERE id BETWEEN 7001 AND 7008
     AND risk_level IN ('高', '高风险', '中', '中风险', '低', '低风险');
  IF v_actual < 3 THEN
    SET v_message = CONCAT('expected high/mid/low risk aliases, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT COUNT(DISTINCT budget_no) INTO v_actual
    FROM ods_budget_v2
   WHERE id BETWEEN 10001 AND 10005;
  IF v_actual <> 5 THEN
    SET v_message = CONCAT('expected 5 unique golden budget numbers, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;

  SELECT
      (SELECT COUNT(*) FROM ods_project_subject_domain_v2)
    + (SELECT COUNT(*) FROM ods_progress_measure_v2)
    + (SELECT COUNT(*) FROM ods_quality_issue_v2)
    + (SELECT COUNT(*) FROM ods_quality_measure_v2)
    + (SELECT COUNT(*) FROM ods_tech_state_v2)
    + (SELECT COUNT(*) FROM ods_tech_state_measure_v2)
    + (SELECT COUNT(*) FROM ods_risk_info_v2)
    + (SELECT COUNT(*) FROM ods_risk_measure_v2)
    + (SELECT COUNT(*) FROM ods_material_info_v2)
    + (SELECT COUNT(*) FROM ods_budget_v2)
    INTO v_actual;
  IF v_actual <> 55 THEN
    SET v_message = CONCAT('expected 55 total golden rows, got ', v_actual);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;
END$$

DELIMITER ;

CALL verify_pjm_golden_seed();
DROP PROCEDURE verify_pjm_golden_seed;

SELECT 'ods_project_subject_domain_v2' AS resource, COUNT(*) AS golden_rows
  FROM ods_project_subject_domain_v2
UNION ALL
SELECT 'ods_progress_measure_v2', COUNT(*)
  FROM ods_progress_measure_v2
UNION ALL
SELECT 'ods_quality_issue_v2', COUNT(*)
  FROM ods_quality_issue_v2
UNION ALL
SELECT 'ods_quality_measure_v2', COUNT(*)
  FROM ods_quality_measure_v2
UNION ALL
SELECT 'ods_tech_state_v2', COUNT(*)
  FROM ods_tech_state_v2
UNION ALL
SELECT 'ods_tech_state_measure_v2', COUNT(*)
  FROM ods_tech_state_measure_v2
UNION ALL
SELECT 'ods_risk_info_v2', COUNT(*)
  FROM ods_risk_info_v2
UNION ALL
SELECT 'ods_risk_measure_v2', COUNT(*)
  FROM ods_risk_measure_v2
UNION ALL
SELECT 'ods_material_info_v2', COUNT(*)
  FROM ods_material_info_v2
UNION ALL
SELECT 'ods_budget_v2', COUNT(*)
  FROM ods_budget_v2;

SELECT 'PASS' AS verification_status, 55 AS golden_rows;
