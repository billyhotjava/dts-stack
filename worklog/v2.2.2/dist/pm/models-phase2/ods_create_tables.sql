-- ============================================================
-- Phase 2 ODS 建表 DDL（手动建表时使用，非 dbt 模型）
-- 所有字段 varchar — 类型转换由 dbt DWD 层处理
-- ============================================================

-- ─── 1. 项目信息表（项目主数据维表）───
CREATE TABLE IF NOT EXISTS ods_project_info (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    project_name        varchar(500),   -- 项目名称
    project_type        varchar(500),   -- 项目类型
    project_level       varchar(500),   -- 项目级别
    dept                varchar(500),   -- 责任科室
    business_unit       varchar(500),   -- 事业部
    project_manager     varchar(500),   -- 项目主管
    dept_leader         varchar(500),   -- 分管室领导
    institute_leader    varchar(500),   -- 所领导
    start_date          varchar(500),   -- 项目开始时间
    plan_end_date       varchar(500),   -- 计划结束时间
    status              varchar(500),   -- 项目状态（策划中/执行中/验收中/已完成/已暂停）
    remark              varchar(500),   -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 2. 质量信息汇总表 ───
CREATE TABLE IF NOT EXISTS ods_quality_issue (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    issue_name          varchar(2000),  -- 问题名称/问题描述
    issue_category      varchar(500),   -- 原因分类（设计/工艺/管理/元器件/操作/外协外购/软件/其他）
    issue_date          varchar(500),   -- 质量问题发生时间
    status              varchar(500),   -- 状态（未完成归零/已完成技术归零/已完成管理归零/已完成技术和管理归零）
    closure_status      varchar(500),   -- 闭环状态（已闭环/未闭环）
    zero_plan           varchar(500),   -- 归零计划（有/无）
    dept                varchar(500),   -- 责任科室
    subsystem           varchar(500),   -- 分系统
    owner               varchar(500),   -- 负责人
    last_update_time    varchar(500),   -- 最后更新时间
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 3. 质量跟进措施表 ───
CREATE TABLE IF NOT EXISTS ods_quality_measure (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    issue_name          varchar(2000),  -- 关联问题名称
    measure_content     varchar(2000),  -- 措施内容
    measure_status      varchar(500),   -- 措施状态
    responsible_person  varchar(500),   -- 责任人
    deadline            varchar(500),   -- 计划完成时间
    actual_complete_date varchar(500),  -- 实际完成时间
    last_update_time    varchar(500),   -- 最后更新时间
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 4. 技术状态信息汇总表 ───
CREATE TABLE IF NOT EXISTS ods_tech_state (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    tech_state_name     varchar(2000),  -- 技术状态名称/代号
    change_item         varchar(2000),  -- 更改事项
    change_category     varchar(500),   -- 更改类别（I/II/III）
    change_submit_time  varchar(500),   -- 更改提出时间
    file_signature_status varchar(500), -- 文件签署状态
    completion_signature varchar(500),  -- 是否完成签署（是/否）
    reform_status       varchar(500),   -- 整改落实状态（未落实整改/已落实整改/不涉及整改）
    closure_status      varchar(500),   -- 闭环状态
    dept                varchar(500),   -- 责任科室
    subsystem           varchar(500),   -- 分系统
    last_update_time    varchar(500),   -- 最后更新时间
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 5. 技术状态跟进措施表 ───
CREATE TABLE IF NOT EXISTS ods_tech_state_measure (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    tech_state_name     varchar(2000),  -- 关联技术状态名称
    measure_content     varchar(2000),  -- 措施内容
    measure_status      varchar(500),   -- 措施状态
    responsible_person  varchar(500),   -- 责任人
    deadline            varchar(500),   -- 计划完成时间
    actual_complete_date varchar(500),  -- 实际完成时间
    last_update_time    varchar(500),   -- 最后更新时间
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 6. 风险信息汇总表 ───
CREATE TABLE IF NOT EXISTS ods_risk_info (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    risk_name           varchar(2000),  -- 风险名称
    risk_level          varchar(500),   -- 风险等级（高/中/低）
    risk_submit_time    varchar(500),   -- 风险提出时间
    risk_content        varchar(2000),  -- 主要风险内容
    impact_scope        varchar(500),   -- 影响范围
    closure_status      varchar(500),   -- 闭环状态（已闭环/未闭环）
    response_measure    varchar(2000),  -- 应对措施
    dept                varchar(500),   -- 责任科室
    subsystem           varchar(500),   -- 分系统
    owner               varchar(500),   -- 负责人
    last_update_time    varchar(500),   -- 最后更新时间
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 7. 风险跟进措施表 ───
CREATE TABLE IF NOT EXISTS ods_risk_measure (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    risk_name           varchar(2000),  -- 关联风险名称
    measure_content     varchar(2000),  -- 措施内容
    measure_status      varchar(500),   -- 措施状态
    responsible_person  varchar(500),   -- 责任人
    deadline            varchar(500),   -- 计划完成时间
    actual_complete_date varchar(500),  -- 实际完成时间
    closure_deliverable varchar(2000),  -- 闭环交付物
    last_update_time    varchar(500),   -- 最后更新时间
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 8. 成本核算基本表 ───
CREATE TABLE IF NOT EXISTS ods_cost_accounting (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号
    project_name        varchar(500),   -- 项目名称
    accounting_period   varchar(500),   -- 统计周期（YYYY-MM 或 YYYY）
    budget_amount       varchar(500),   -- 预算金额（万元）
    actual_amount       varchar(500),   -- 实际金额（万元）
    dept                varchar(500),   -- 责任科室/部门
    cost_category       varchar(500),   -- 费用类别
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);
