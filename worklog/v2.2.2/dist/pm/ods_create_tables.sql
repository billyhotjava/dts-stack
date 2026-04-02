-- ============================================================
-- 项目管理 ODS 建表 DDL（手动建表时使用，非 dbt 模型）
-- 注意: 执行此脚本会 DROP 所有 ODS 表并重建！已有数据会丢失。
-- 所有字段 varchar — 类型转换由 dbt DWD 层处理
-- ============================================================

-- ─── 1. 项目主体域原始数据（执行域） ───
DROP TABLE IF EXISTS ods_project_subject_domain CASCADE;
CREATE TABLE ods_project_subject_domain (
    id                   serial PRIMARY KEY,
    project_no           varchar(500),   -- 项目编号
    subsystem            varchar(500),   -- 分系统/分任务
    node_task            varchar(500),   -- 节点任务及目标
    plan_date            varchar(500),   -- 节点计划时间
    plan_week            varchar(500),   -- 节点计划周数
    deliverable          varchar(500),   -- 交付物
    node_type            varchar(500),   -- 节点类型（一般节点/重要节点/重大节点/里程碑节点）
    owner                varchar(500),   -- 负责人
    dept                 varchar(500),   -- 责任科室
    dept_leader          varchar(500),   -- 分管室领导
    completion_status    varchar(500),   -- 完成情况
    collab_dept          varchar(500),   -- 协同部门
    supervisor_dept      varchar(500),   -- 责任监管部门
    delay_expected_date  varchar(500),   -- 延期预计完成时间
    incomplete_reason    varchar(500),   -- 未完成原因及当前进展
    risk_level           varchar(500),   -- 风险等级（高/中/低）
    risk_content         varchar(500),   -- 主要风险内容及措施
    delay_impact         varchar(500),   -- 延期影响分析
    actual_date          varchar(500),   -- 实际完成时间
    actual_week          varchar(500),   -- 实际完成周数
    institute_leader     varchar(500),   -- 所领导
    source               varchar(500),   -- 来源
    original_plan_date   varchar(500),   -- 延期项目原计划时间
    delay_days_changed   varchar(500),   -- 计划延误时间（已变更）
    delay_days_unchanged varchar(500),   -- 计划延误时间（未变更）
    delay_applied        varchar(500),   -- 是否提交延期申请
    project_manager      varchar(500),   -- 项目主管
    last_update_time     varchar(500),   -- 最后更新时间
    last_update_week     varchar(500),   -- 最后更新周数
    filled_by            varchar(500),   -- 填写人
    highlight            varchar(500),   -- 亮点工作
    source_system        varchar(200) DEFAULT 'excel',
    import_time          timestamp DEFAULT now()
);

-- ─── 2. 质量信息汇总表 ───
DROP TABLE IF EXISTS ods_quality_issue CASCADE;
CREATE TABLE ods_quality_issue (
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
DROP TABLE IF EXISTS ods_quality_measure CASCADE;
CREATE TABLE ods_quality_measure (
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
DROP TABLE IF EXISTS ods_tech_state CASCADE;
CREATE TABLE ods_tech_state (
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
DROP TABLE IF EXISTS ods_tech_state_measure CASCADE;
CREATE TABLE ods_tech_state_measure (
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
DROP TABLE IF EXISTS ods_risk_info CASCADE;
CREATE TABLE ods_risk_info (
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
DROP TABLE IF EXISTS ods_risk_measure CASCADE;
CREATE TABLE ods_risk_measure (
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
DROP TABLE IF EXISTS ods_cost_accounting CASCADE;
CREATE TABLE ods_cost_accounting (
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

-- ─── 9. 重要物料信息表 ───
DROP TABLE IF EXISTS ods_material_info CASCADE;
CREATE TABLE ods_material_info (
    id                  serial PRIMARY KEY,
    project_no          varchar(500),   -- 项目编号（下拉选择）
    subsystem           varchar(500),   -- 分系统/分任务
    pbs_no              varchar(500),   -- PBS编号
    pbs_name            varchar(500),   -- PBS名称
    risk_name           varchar(2000),  -- 风险名称
    risk_description    varchar(2000),  -- 风险描述（按条目梳理当前存在的主要具体风险点）
    self_or_outsource   varchar(500),   -- 自研或外协
    supplier_name       varchar(500),   -- 供应商名称
    is_long_cycle       varchar(500),   -- 是否长周期物料（下拉选择）
    belonging_unit      varchar(500),   -- 所属单机
    delivery_date       varchar(500),   -- 交期时间
    last_update_time    varchar(500),   -- 最后更新时间
    last_update_week    varchar(500),   -- 最后更新周数
    filled_by           varchar(500),   -- 填写人
    remark              varchar(2000),  -- 备注
    source_system       varchar(200) DEFAULT 'excel',
    import_time         timestamp DEFAULT now()
);

-- ─── 10. 进度跟进措施表 ───
DROP TABLE IF EXISTS ods_progress_measure CASCADE;
CREATE TABLE ods_progress_measure (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    subsystem                varchar(500),   -- 分系统/分任务
    node_task                varchar(500),   -- 节点任务及目标
    plan_date                varchar(500),   -- 节点计划时间（格式：XXXX-XX-XX）
    plan_week                varchar(500),   -- 节点计划周数
    completion_status        varchar(500),   -- 完成情况（下拉选择）
    measure_category         varchar(500),   -- 跟进措施类别（下拉选择）
    measure_title            varchar(2000),  -- 跟进措施题目
    follow_up_person         varchar(500),   -- 跟进人
    main_recipient           varchar(500),   -- 主送
    cc_recipient             varchar(500),   -- 抄送
    follow_up_date           varchar(500),   -- 跟进时间（格式：XXXX-XX-XX）
    follow_up_week           varchar(500),   -- 跟进周数
    closure_status           varchar(500),   -- 闭环状态（下拉选择）
    final_closure_date       varchar(500),   -- 最终闭环时间（格式：XXXX-XX-XX）
    final_closure_week       varchar(500),   -- 最终闭环周数
    closure_deliverable_type varchar(500),   -- 闭环交付物类别（下拉选择）
    closure_deliverable      varchar(2000),  -- 闭环交付物
    risk_content             varchar(2000),  -- 主要风险内容
    last_update_time         varchar(500),   -- 最后更新时间（格式：XXXX-XX-XX）
    last_update_week         varchar(500),   -- 最后更新周数
    remark                   varchar(2000),  -- 备注
    filled_by                varchar(500),   -- 填写人
    source_system            varchar(200) DEFAULT 'excel',
    import_time              timestamp DEFAULT now()
);
