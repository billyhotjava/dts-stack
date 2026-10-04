-- ============================================================
-- 增量升级 DDL：补齐当前 PJM 缺失的 6 张 ODS v2 表
--
-- 适用场景：目标 PostgreSQL public schema 已存在部分 PJM ODS v2 表，
--           仅补齐以下缺失对象：
--           ods_progress_measure_v2
--           ods_quality_issue_v2
--           ods_tech_state_measure_v2
--           ods_risk_info_v2
--           ods_risk_measure_v2
--           ods_budget_v2
--
-- 安全性：
--   - 仅 CREATE TABLE IF NOT EXISTS，不 DROP、不 CASCADE、不写业务数据。
--   - 复用 09_add_ods_budget_v2.sql 创建预算表。
--   - 六张表在同一事务中创建，任一语句失败则整体回滚。
--   - 目标 schema 固定为 public，避免 search_path 漂移。
--   - 默认 owner 为 biadmin；其他环境可通过 -v ods_owner=<role> 覆盖。
--
-- 回滚说明：
--   本迁移为前向迁移。若在尚未导入数据、尚未建立依赖时必须撤销，
--   应另建前向回滚脚本，按依赖逆序 DROP TABLE（不得使用 CASCADE）。
-- ============================================================

\set ON_ERROR_STOP on

\if :{?ods_owner}
\else
\set ods_owner biadmin
\endif

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '2min';
SET LOCAL search_path = public;

-- 预算执行台账表：复用已评审的单表增量脚本。
\ir 09_add_ods_budget_v2.sql

-- 进度跟进措施表（23 个业务字段）
CREATE TABLE IF NOT EXISTS public.ods_progress_measure_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),
    subsystem                varchar(500),
    node_task                varchar(500),
    plan_date                varchar(500),
    plan_week                varchar(500),
    completion_status        varchar(500),
    measure_category         varchar(500),
    measure_title            varchar(2000),
    follow_up_person         varchar(500),
    main_recipient           varchar(500),
    cc_recipient             varchar(500),
    follow_up_date           varchar(500),
    follow_up_week           varchar(500),
    closure_status           varchar(500),
    final_closure_date       varchar(500),
    final_closure_week       varchar(500),
    closure_deliverable_type varchar(500),
    closure_deliverable      varchar(2000),
    risk_content             varchar(2000),
    last_update_time         varchar(500),
    last_update_week         varchar(500),
    remark                   varchar(2000),
    filled_by                varchar(500),
    _dts_source_system       varchar(500) DEFAULT 'excel',
    _dts_import_time         timestamp DEFAULT now()
);

-- 质量信息汇总表（21 个业务字段）
CREATE TABLE IF NOT EXISTS public.ods_quality_issue_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),
    subsystem                varchar(500),
    issue_name               varchar(2000),
    dept                     varchar(500),
    team_leader              varchar(500),
    dept_leader              varchar(500),
    issue_date               varchar(500),
    issue_week               varchar(500),
    issue_summary            varchar(2000),
    issue_category           varchar(500),
    zero_plan                varchar(500),
    zero_plan_synced         varchar(500),
    new_plan_count           varchar(500),
    status                   varchar(500),
    current_progress         varchar(2000),
    zero_complete_date       varchar(500),
    zero_complete_week       varchar(500),
    last_update_time         varchar(500),
    last_update_week         varchar(500),
    project_manager          varchar(500),
    filled_by                varchar(500),
    _dts_source_system       varchar(500) DEFAULT 'excel',
    _dts_import_time         timestamp DEFAULT now()
);

-- 技术状态跟进措施表（41 个业务字段）
CREATE TABLE IF NOT EXISTS public.ods_tech_state_measure_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),
    tech_state_name          varchar(2000),
    change_item              varchar(2000),
    owner                    varchar(500),
    dept                     varchar(500),
    dept_leader              varchar(500),
    change_submit_time       varchar(500),
    change_submit_week       varchar(500),
    completion_signature     varchar(500),
    signature_closure_date   varchar(500),
    signature_closure_week   varchar(500),
    change_reason            varchar(2000),
    change_category          varchar(500),
    plan_file_closure_date   varchar(500),
    plan_file_closure_week   varchar(500),
    plan_reform_date         varchar(500),
    plan_reform_week         varchar(500),
    plan_synced              varchar(500),
    new_plan_count           varchar(500),
    affected_files           varchar(2000),
    affected_objects         varchar(2000),
    file_signature_status    varchar(500),
    reform_status            varchar(500),
    project_manager          varchar(500),
    measure_category         varchar(500),
    measure_title            varchar(2000),
    follow_up_person         varchar(500),
    main_recipient           varchar(500),
    cc_recipient             varchar(500),
    follow_up_date           varchar(500),
    follow_up_week           varchar(500),
    closure_status           varchar(500),
    final_closure_date       varchar(500),
    final_closure_week       varchar(500),
    closure_deliverable_type varchar(500),
    closure_deliverable      varchar(2000),
    risk_content             varchar(2000),
    last_update_time         varchar(500),
    last_update_week         varchar(500),
    remark                   varchar(2000),
    filled_by                varchar(500),
    _dts_source_system       varchar(500) DEFAULT 'excel',
    _dts_import_time         timestamp DEFAULT now()
);

-- 风险信息汇总表（31 个业务字段）
CREATE TABLE IF NOT EXISTS public.ods_risk_info_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),
    risk_name                varchar(2000),
    subsystem                varchar(500),
    belonging_unit           varchar(500),
    risk_description         varchar(2000),
    risk_submit_time         varchar(500),
    risk_submit_week         varchar(500),
    risk_phase               varchar(500),
    risk_category            varchar(500),
    risk_level               varchar(500),
    impact_scope             varchar(2000),
    response_measure         varchar(2000),
    final_release_time       varchar(500),
    final_release_week       varchar(500),
    monthly_control_plan     varchar(2000),
    weekly_release_plan      varchar(2000),
    release_plan_synced      varchar(500),
    new_plan_count           varchar(500),
    progress_stat_time       varchar(500),
    progress_stat_week       varchar(500),
    progress_situation       varchar(2000),
    response_owner           varchar(500),
    control_owner            varchar(500),
    dept                     varchar(500),
    risk_status              varchar(500),
    risk_release_date        varchar(500),
    risk_release_week        varchar(500),
    remark                   varchar(2000),
    last_update_time         varchar(500),
    last_update_week         varchar(500),
    filled_by                varchar(500),
    _dts_source_system       varchar(500) DEFAULT 'excel',
    _dts_import_time         timestamp DEFAULT now()
);

-- 风险跟进措施表（42 个业务字段）
CREATE TABLE IF NOT EXISTS public.ods_risk_measure_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),
    risk_name                varchar(2000),
    subsystem                varchar(500),
    belonging_unit           varchar(500),
    risk_description         varchar(2000),
    risk_submit_time         varchar(500),
    risk_submit_week         varchar(500),
    risk_phase               varchar(500),
    risk_category            varchar(500),
    risk_level               varchar(500),
    impact_scope             varchar(2000),
    response_measure         varchar(2000),
    final_release_time       varchar(500),
    monthly_control_plan     varchar(2000),
    weekly_release_plan      varchar(2000),
    release_plan_synced      varchar(500),
    new_plan_count           varchar(500),
    progress_stat_time       varchar(500),
    progress_stat_week       varchar(500),
    progress_situation       varchar(2000),
    response_owner           varchar(500),
    control_owner            varchar(500),
    dept                     varchar(500),
    risk_status              varchar(500),
    project_manager          varchar(500),
    measure_category         varchar(500),
    measure_title            varchar(2000),
    follow_up_person         varchar(500),
    main_recipient           varchar(500),
    cc_recipient             varchar(500),
    follow_up_date           varchar(500),
    follow_up_week           varchar(500),
    closure_status           varchar(500),
    final_closure_date       varchar(500),
    final_closure_week       varchar(500),
    closure_deliverable_type varchar(500),
    closure_deliverable      varchar(2000),
    risk_content             varchar(2000),
    last_update_time         varchar(500),
    last_update_week         varchar(500),
    remark                   varchar(2000),
    filled_by                varchar(500),
    _dts_source_system       varchar(500) DEFAULT 'excel',
    _dts_import_time         timestamp DEFAULT now()
);

-- 与既有 PJM ODS 保持相同 owner，确保 dbt/接入账号权限一致。
ALTER TABLE public.ods_progress_measure_v2 OWNER TO :"ods_owner";
ALTER TABLE public.ods_quality_issue_v2 OWNER TO :"ods_owner";
ALTER TABLE public.ods_tech_state_measure_v2 OWNER TO :"ods_owner";
ALTER TABLE public.ods_risk_info_v2 OWNER TO :"ods_owner";
ALTER TABLE public.ods_risk_measure_v2 OWNER TO :"ods_owner";
ALTER TABLE public.ods_budget_v2 OWNER TO :"ods_owner";

ALTER SEQUENCE public.ods_progress_measure_v2_id_seq OWNER TO :"ods_owner";
ALTER SEQUENCE public.ods_quality_issue_v2_id_seq OWNER TO :"ods_owner";
ALTER SEQUENCE public.ods_tech_state_measure_v2_id_seq OWNER TO :"ods_owner";
ALTER SEQUENCE public.ods_risk_info_v2_id_seq OWNER TO :"ods_owner";
ALTER SEQUENCE public.ods_risk_measure_v2_id_seq OWNER TO :"ods_owner";
ALTER SEQUENCE public.ods_budget_v2_id_seq OWNER TO :"ods_owner";

-- 执行内校验：六张目标表必须全部存在。
DO $$
DECLARE
    missing_count integer;
BEGIN
    SELECT count(*)
      INTO missing_count
      FROM (VALUES
          ('ods_progress_measure_v2'),
          ('ods_quality_issue_v2'),
          ('ods_tech_state_measure_v2'),
          ('ods_risk_info_v2'),
          ('ods_risk_measure_v2'),
          ('ods_budget_v2')
      ) AS expected(table_name)
      LEFT JOIN information_schema.tables actual
        ON actual.table_schema = 'public'
       AND actual.table_name = expected.table_name
     WHERE actual.table_name IS NULL;

    IF missing_count <> 0 THEN
        RAISE EXCEPTION 'PJM ODS migration incomplete: % table(s) missing', missing_count;
    END IF;
END
$$;

COMMIT;
