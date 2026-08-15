-- Data phase for existing PJM MySQL test sources.
-- Preserves valid existing values and deterministically fills missing/invalid
-- values so direct JDBC and API ingestion expose realistic security scope.

USE dts_pjm_test;

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE ods_project_subject_domain_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 1001 AND 1010;

UPDATE ods_progress_measure_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 2001 AND 2002;

UPDATE ods_quality_issue_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 3001 AND 3014;

UPDATE ods_quality_measure_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 4001 AND 4002;

UPDATE ods_tech_state_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 5001 AND 5008;

UPDATE ods_tech_state_measure_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 6001 AND 6002;

UPDATE ods_risk_info_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 7001 AND 7008;

UPDATE ods_risk_measure_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 8001 AND 8002;

UPDATE ods_material_info_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 9001 AND 9002;

UPDATE ods_budget_v2
   SET classification = CASE
         WHEN UPPER(TRIM(COALESCE(classification, ''))) IN ('PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
           THEN UPPER(TRIM(classification))
         ELSE ELT(MOD(id, 4) + 1, 'PUBLIC', 'INTERNAL', 'SECRET', 'CONFIDENTIAL')
       END,
       owner_dept = COALESCE(
         NULLIF(TRIM(owner_dept), ''),
         CASE project_no WHEN 'PJM-A' THEN 'DEPT_PJM_A' WHEN 'PJM-B' THEN 'DEPT_PJM_B' ELSE 'DEPT_TEST' END
       )
 WHERE id BETWEEN 10001 AND 10005;

COMMIT;
