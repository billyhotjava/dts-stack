-- Contract phase for existing PJM MySQL test sources.
-- Run only after 05_backfill_source_security_values.sql succeeds.

USE dts_pjm_test;

DROP PROCEDURE IF EXISTS validate_pjm_source_security_values;

DELIMITER $$

CREATE PROCEDURE validate_pjm_source_security_values()
BEGIN
  DECLARE v_invalid INT DEFAULT 0;

  SELECT COUNT(*) INTO v_invalid
    FROM (
      SELECT classification, owner_dept FROM ods_project_subject_domain_v2
      UNION ALL SELECT classification, owner_dept FROM ods_progress_measure_v2
      UNION ALL SELECT classification, owner_dept FROM ods_quality_issue_v2
      UNION ALL SELECT classification, owner_dept FROM ods_quality_measure_v2
      UNION ALL SELECT classification, owner_dept FROM ods_tech_state_v2
      UNION ALL SELECT classification, owner_dept FROM ods_tech_state_measure_v2
      UNION ALL SELECT classification, owner_dept FROM ods_risk_info_v2
      UNION ALL SELECT classification, owner_dept FROM ods_risk_measure_v2
      UNION ALL SELECT classification, owner_dept FROM ods_material_info_v2
      UNION ALL SELECT classification, owner_dept FROM ods_budget_v2
    ) source_rows
   WHERE classification IS NULL
      OR BINARY classification NOT IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
      OR owner_dept IS NULL
      OR owner_dept NOT REGEXP '^[A-Za-z0-9._-]{1,64}$';

  IF v_invalid <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'source security backfill incomplete; refusing NOT NULL enforcement';
  END IF;
END$$

DELIMITER ;

CALL validate_pjm_source_security_values();
DROP PROCEDURE validate_pjm_source_security_values;

ALTER TABLE ods_project_subject_domain_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_progress_measure_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_quality_issue_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_quality_measure_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_tech_state_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_tech_state_measure_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_risk_info_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_risk_measure_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_material_info_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';

ALTER TABLE ods_budget_v2
  MODIFY classification VARCHAR(32) NOT NULL COMMENT '数据密级：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL',
  MODIFY owner_dept VARCHAR(64) NOT NULL COMMENT '所属部门编码';
