-- Expand phase for existing PJM MySQL test sources.
-- Adds nullable columns first so the following data backfill can run safely.
-- Compatible with MySQL 5.7+ (does not rely on ADD COLUMN IF NOT EXISTS).

USE dts_pjm_test;

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_pjm_source_security_columns;

DELIMITER $$

CREATE PROCEDURE add_pjm_source_security_columns(IN p_table_name VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND column_name = 'classification'
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE `',
      p_table_name,
      '` ADD COLUMN `classification` VARCHAR(32) NULL ',
      'COMMENT ''数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL'' AFTER `id`'
    );
    PREPARE ddl_statement FROM @ddl;
    EXECUTE ddl_statement;
    DEALLOCATE PREPARE ddl_statement;
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND column_name = 'owner_dept'
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE `',
      p_table_name,
      '` ADD COLUMN `owner_dept` VARCHAR(64) NULL ',
      'COMMENT ''所属部门编码'' AFTER `classification`'
    );
    PREPARE ddl_statement FROM @ddl;
    EXECUTE ddl_statement;
    DEALLOCATE PREPARE ddl_statement;
  END IF;
END$$

DELIMITER ;

CALL add_pjm_source_security_columns('ods_project_subject_domain_v2');
CALL add_pjm_source_security_columns('ods_progress_measure_v2');
CALL add_pjm_source_security_columns('ods_quality_issue_v2');
CALL add_pjm_source_security_columns('ods_quality_measure_v2');
CALL add_pjm_source_security_columns('ods_tech_state_v2');
CALL add_pjm_source_security_columns('ods_tech_state_measure_v2');
CALL add_pjm_source_security_columns('ods_risk_info_v2');
CALL add_pjm_source_security_columns('ods_risk_measure_v2');
CALL add_pjm_source_security_columns('ods_material_info_v2');
CALL add_pjm_source_security_columns('ods_budget_v2');

DROP PROCEDURE add_pjm_source_security_columns;
