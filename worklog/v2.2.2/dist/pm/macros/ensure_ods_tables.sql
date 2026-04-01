{% macro ensure_ods_tables() %}
{#
  ODS 表初始化宏
  - 默认行为: CREATE TABLE IF NOT EXISTS（保留已有数据）
  - 传入 force_rebuild=true 时: DROP + CREATE（清空重建）

  用法:
    on-run-start:
      - "{{ ensure_ods_tables() }}"                    # 安全模式，不丢数据
    手动重建:
      dbt run-operation ensure_ods_tables --args '{force_rebuild: true}'
#}

{% set force = var('force_rebuild', false) %}

{% set ddl %}

{% if force %}
DROP TABLE IF EXISTS ods_project_subject_domain CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_project_subject_domain (
    id serial PRIMARY KEY, project_no varchar(500), subsystem varchar(500),
    node_task varchar(500), plan_date varchar(500), plan_week varchar(500),
    deliverable varchar(500), node_type varchar(500), owner varchar(500),
    dept varchar(500), dept_leader varchar(500), completion_status varchar(500),
    collab_dept varchar(500), supervisor_dept varchar(500), delay_expected_date varchar(500),
    incomplete_reason varchar(500), risk_level varchar(500), risk_content varchar(500),
    delay_impact varchar(500), actual_date varchar(500), actual_week varchar(500),
    institute_leader varchar(500), source varchar(500), original_plan_date varchar(500),
    delay_days_changed varchar(500), delay_days_unchanged varchar(500),
    delay_applied varchar(500), project_manager varchar(500), last_update_time varchar(500),
    last_update_week varchar(500), filled_by varchar(500), highlight varchar(500),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_quality_issue CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_quality_issue (
    id serial PRIMARY KEY, project_no varchar(500), issue_name varchar(2000),
    issue_category varchar(500), issue_date varchar(500), status varchar(500),
    closure_status varchar(500), zero_plan varchar(500), dept varchar(500),
    subsystem varchar(500), owner varchar(500), last_update_time varchar(500),
    filled_by varchar(500), remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_quality_measure CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_quality_measure (
    id serial PRIMARY KEY, project_no varchar(500), issue_name varchar(2000),
    measure_content varchar(2000), measure_status varchar(500),
    responsible_person varchar(500), deadline varchar(500),
    actual_complete_date varchar(500), last_update_time varchar(500),
    filled_by varchar(500), remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_tech_state CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_tech_state (
    id serial PRIMARY KEY, project_no varchar(500), tech_state_name varchar(2000),
    change_item varchar(2000), change_category varchar(500),
    change_submit_time varchar(500), file_signature_status varchar(500),
    completion_signature varchar(500), reform_status varchar(500),
    closure_status varchar(500), dept varchar(500), subsystem varchar(500),
    last_update_time varchar(500), filled_by varchar(500), remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_tech_state_measure CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_tech_state_measure (
    id serial PRIMARY KEY, project_no varchar(500), tech_state_name varchar(2000),
    measure_content varchar(2000), measure_status varchar(500),
    responsible_person varchar(500), deadline varchar(500),
    actual_complete_date varchar(500), last_update_time varchar(500),
    filled_by varchar(500), remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_risk_info CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_risk_info (
    id serial PRIMARY KEY, project_no varchar(500), risk_name varchar(2000),
    risk_level varchar(500), risk_submit_time varchar(500),
    risk_content varchar(2000), impact_scope varchar(500),
    closure_status varchar(500), response_measure varchar(2000),
    dept varchar(500), subsystem varchar(500), owner varchar(500),
    last_update_time varchar(500), filled_by varchar(500), remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_risk_measure CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_risk_measure (
    id serial PRIMARY KEY, project_no varchar(500), risk_name varchar(2000),
    measure_content varchar(2000), measure_status varchar(500),
    responsible_person varchar(500), deadline varchar(500),
    actual_complete_date varchar(500), closure_deliverable varchar(2000),
    last_update_time varchar(500), filled_by varchar(500), remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% if force %}
DROP TABLE IF EXISTS ods_cost_accounting CASCADE;
{% endif %}
CREATE TABLE IF NOT EXISTS ods_cost_accounting (
    id serial PRIMARY KEY, project_no varchar(500), project_name varchar(500),
    accounting_period varchar(500), budget_amount varchar(500),
    actual_amount varchar(500), dept varchar(500), cost_category varchar(500),
    remark varchar(2000),
    source_system varchar(200) DEFAULT 'excel', import_time timestamp DEFAULT now()
);

{% endset %}

{% do run_query(ddl) %}
{% if force %}
{% do log("ODS tables force-rebuilt (8 tables dropped + created)", info=true) %}
{% else %}
{% do log("ODS tables ensured (8 tables, existing data preserved)", info=true) %}
{% endif %}

{% endmacro %}
