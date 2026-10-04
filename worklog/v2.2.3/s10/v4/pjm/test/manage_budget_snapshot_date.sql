-- 受控管理单个测试导入批次的预算业务快照日期。
--
-- 必填参数：
--   mode              plan | apply | rollback
--   snapshot_date     业务确认的快照日，如 2026-08-12
--   import_batch_time 要处理的 _dts_import_time 精确值
--
-- 示例：
--   psql ... -v mode=plan -v snapshot_date=2026-08-12 \
--     -v import_batch_time='2026-08-12 18:19:26.912117' \
--     -f manage_budget_snapshot_date.sql
--
-- 本脚本只处理 snapshot_date IS NULL 的指定批次；rollback 也同时
-- 校验指定批次和指定快照日，避免影响其他导入。
\set ON_ERROR_STOP on

\if :{?mode}
\else
  \echo 'ERROR: mode is required (plan|apply|rollback)'
  \quit
\endif
\if :{?snapshot_date}
\else
  \echo 'ERROR: snapshot_date is required'
  \quit
\endif
\if :{?import_batch_time}
\else
  \echo 'ERROR: import_batch_time is required'
  \quit
\endif

SELECT :'mode' = 'apply' AS do_apply,
       :'mode' = 'rollback' AS do_rollback
\gset

SELECT count(*) AS candidate_rows,
       count(DISTINCT budget_no) AS candidate_budget_nos
FROM ods_budget_v2
WHERE _dts_import_time = :'import_batch_time'::timestamp
  AND snapshot_date IS NULL;

\if :do_apply
  BEGIN;
  UPDATE ods_budget_v2
  SET snapshot_date = :'snapshot_date'::date
  WHERE _dts_import_time = :'import_batch_time'::timestamp
    AND snapshot_date IS NULL;
  COMMIT;
\elif :do_rollback
  BEGIN;
  UPDATE ods_budget_v2
  SET snapshot_date = NULL
  WHERE _dts_import_time = :'import_batch_time'::timestamp
    AND snapshot_date = :'snapshot_date'::date;
  COMMIT;
\endif

SELECT snapshot_date, count(*) AS rows, count(DISTINCT budget_no) AS budget_nos
FROM ods_budget_v2
WHERE _dts_import_time = :'import_batch_time'::timestamp
GROUP BY snapshot_date
ORDER BY snapshot_date NULLS FIRST;
