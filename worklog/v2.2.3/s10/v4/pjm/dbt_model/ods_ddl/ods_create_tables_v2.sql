-- ============================================================
-- 项目管理 ODS 建表 DDL v2（手动建表，非 dbt 模型）
-- 所有表名增加 _v2 后缀，与 v1 表隔离共存
-- 来源: v2/ods-verify 字段定义 + v1 DDL 基线
-- 注意: 执行此脚本会 DROP 所有 _v2 ODS 表并重建！已有数据会丢失。
-- 所有字段 varchar — 类型转换由 dbt DWD 层处理
-- ============================================================

-- ─── 0. 进度信息汇总表（来源: project1.xlsx，33个业务字段） ───
DROP TABLE IF EXISTS ods_project_subject_domain_v2 CASCADE;
CREATE TABLE ods_project_subject_domain_v2 (
    id                   serial PRIMARY KEY,
    project_no           varchar(500),   -- 项目编号
    subsystem            varchar(500),   -- 分系统/分任务
    node_task            varchar(500),   -- 节点任务及目标
    plan_start_date      varchar(500),   -- 计划开始日期（格式：XXXX-XX-XX）
    plan_date            varchar(500),   -- 节点计划时间（格式：XXXX-XX-XX）
    plan_week            varchar(500),   -- 节点计划周数
    deliverable          varchar(2000),  -- 交付物
    node_type            varchar(500),   -- 节点类型（下拉选择：一般/重要/重大/里程碑）
    owner                varchar(500),   -- 负责人
    dept                 varchar(500),   -- 责任科室（下拉选择）
    dept_leader          varchar(500),   -- 分管室领导
    completion_status    varchar(500),   -- 完成情况（下拉选择）
    collab_dept          varchar(500),   -- 协同部门
    supervisor_dept      varchar(500),   -- 责任监管部门
    delay_expected_date  varchar(500),   -- 延期预计完成时间（格式：XXXX-XX-XX）
    incomplete_reason    varchar(2000),  -- 未完成原因及当前进展
    risk_level           varchar(500),   -- 风险等级（下拉选择：高/中/低）
    risk_content         varchar(2000),  -- 主要风险内容及措施
    delay_impact         varchar(2000),  -- 延期影响分析
    actual_start_date    varchar(500),   -- 实际开始日期（格式：XXXX-XX-XX）
    actual_date          varchar(500),   -- 实际完成时间（格式：XXXX-XX-XX）
    actual_week          varchar(500),   -- 实际完成周数
    institute_leader     varchar(500),   -- 所领导
    source               varchar(500),   -- 来源
    original_plan_date   varchar(500),   -- 延期项目原计划时间（格式：XXXX-XX-XX）
    delay_days_changed   varchar(500),   -- 计划延误时间（已变更）
    delay_days_unchanged varchar(500),   -- 计划延误时间（未变更）
    delay_applied        varchar(500),   -- 是否提交延期申请（下拉选择）
    project_manager      varchar(500),   -- 项目主管（下拉选择）
    last_update_time     varchar(500),   -- 最后更新时间（格式：XXXX-XX-XX）
    last_update_week     varchar(500),   -- 最后更新周数
    filled_by            varchar(500),   -- 填写人
    highlight            varchar(2000),  -- 亮点工作
    source_system        varchar(200) DEFAULT 'excel',
    source_file          varchar(1000), -- 来源文件名/路径
    sheet_name           varchar(500),  -- 来源 sheet 名称
    batch_id             varchar(500),  -- 导入批次 ID
    row_num              varchar(500),  -- 来源行号（原始值）
    import_time          timestamp DEFAULT now()
);

-- ─── 1. 进度跟进措施表（23个业务字段） ───
DROP TABLE IF EXISTS ods_progress_measure_v2 CASCADE;
CREATE TABLE ods_progress_measure_v2 (
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
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 2. 质量信息汇总表（21个业务字段） ───
DROP TABLE IF EXISTS ods_quality_issue_v2 CASCADE;
CREATE TABLE ods_quality_issue_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    subsystem                varchar(500),   -- 分系统/分任务
    issue_name               varchar(2000),  -- 问题名称
    dept                     varchar(500),   -- 责任单位（下拉选择）
    team_leader              varchar(500),   -- 团队及负责人
    dept_leader              varchar(500),   -- 责任单位领导
    issue_date               varchar(500),   -- 质量问题发生时间（格式：XXXX-XX-XX）
    issue_week               varchar(500),   -- 质量问题发生周数
    issue_summary            varchar(2000),  -- 问题概述
    issue_category           varchar(500),   -- 原因分类（下拉选择）
    zero_plan                varchar(500),   -- 归零计划
    zero_plan_synced         varchar(500),   -- 归零计划是否已在进度信息汇总表中更新（下拉选择）
    new_plan_count           varchar(500),   -- 新增计划数量
    status                   varchar(500),   -- 状态（下拉选择）
    current_progress         varchar(2000),  -- 当前进展
    zero_complete_date       varchar(500),   -- 归零完成时间（格式：XXXX-XX-XX）
    zero_complete_week       varchar(500),   -- 归零完成周数
    last_update_time         varchar(500),   -- 最后更新时间（格式：XXXX-XX-XX）
    last_update_week         varchar(500),   -- 最后更新周数
    project_manager          varchar(500),   -- 项目主管（下拉选择）
    filled_by                varchar(500),   -- 填写人
    source_system            varchar(200) DEFAULT 'excel',
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 3. 质量跟进措施表（33个业务字段） ───
DROP TABLE IF EXISTS ods_quality_measure_v2 CASCADE;
CREATE TABLE ods_quality_measure_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    subsystem                varchar(500),   -- 分系统/分任务
    issue_name               varchar(2000),  -- 问题名称
    dept                     varchar(500),   -- 责任单位（下拉选择）
    team_leader              varchar(500),   -- 团队及负责人
    dept_leader              varchar(500),   -- 责任单位领导
    issue_date               varchar(500),   -- 质量问题发生时间（格式：XXXX-XX-XX）
    issue_week               varchar(500),   -- 质量问题发生周数
    issue_summary            varchar(2000),  -- 问题概述
    issue_category           varchar(500),   -- 原因分类（下拉选择）
    zero_plan                varchar(500),   -- 归零计划
    zero_plan_synced         varchar(500),   -- 归零计划是否已在进度信息汇总表中更新（下拉选择）
    new_plan_count           varchar(500),   -- 新增计划数量
    status                   varchar(500),   -- 状态（下拉选择）
    current_progress         varchar(2000),  -- 当前进展
    project_manager          varchar(500),   -- 项目主管（下拉选择）
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
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 4. 技术状态信息汇总表（33个业务字段） ───
DROP TABLE IF EXISTS ods_tech_state_v2 CASCADE;
CREATE TABLE ods_tech_state_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    tech_state_name          varchar(2000),  -- 技术状态项名称、代号
    change_item              varchar(2000),  -- 更改事项
    owner                    varchar(500),   -- 负责人
    dept                     varchar(500),   -- 责任科室（下拉选择）
    dept_leader              varchar(500),   -- 分管室领导
    change_submit_time       varchar(500),   -- 更改提出时间（格式：XXXX-XX-XX）
    change_submit_week       varchar(500),   -- 更改提出周数
    completion_signature     varchar(500),   -- 技术状态变更单是否完成签署（下拉选择）
    signature_closure_date   varchar(500),   -- 技术状态变更单闭环签署时间（格式：XXXX-XX-XX）
    signature_closure_week   varchar(500),   -- 技术状态变更单闭环签署周数
    change_reason            varchar(2000),  -- 简要更改原因及内容
    change_category          varchar(500),   -- 更改类别（下拉选择：I/II/III）
    plan_file_closure_date   varchar(500),   -- 计划完成文件闭环签署时间（格式：XXXX-XX-XX）
    plan_file_closure_week   varchar(500),   -- 计划完成文件闭环签署周数
    plan_reform_date         varchar(500),   -- 计划完成整改落实时间（格式：XXXX-XX-XX）
    plan_reform_week         varchar(500),   -- 计划完成整改落实周数
    plan_synced              varchar(500),   -- 计划是否已在进度信息汇总表中更新（下拉选择）
    new_plan_count           varchar(500),   -- 新增计划数量
    review_situation         varchar(2000),  -- 更改论证情况/评估评审情况
    affected_files           varchar(2000),  -- 受影响文件
    affected_objects         varchar(2000),  -- 受影响实物（PBS名称）
    file_signature_status    varchar(500),   -- 文件签署状态（下拉选择）
    file_signature_date      varchar(500),   -- 文件完成签署时间（格式：XXXX-XX-XX）
    file_signature_week      varchar(500),   -- 文件完成签署周数
    reform_status            varchar(500),   -- 整改落实状态（下拉选择）
    reform_date              varchar(500),   -- 整改落实时间（格式：XXXX-XX-XX）
    reform_week              varchar(500),   -- 整改落实周数
    project_manager          varchar(500),   -- 项目主管（下拉选择）
    last_update_time         varchar(500),   -- 最后更新时间（格式：XXXX-XX-XX）
    last_update_week         varchar(500),   -- 最后更新周数
    filled_by                varchar(500),   -- 填写人
    remark                   varchar(2000),  -- 备注
    source_system            varchar(200) DEFAULT 'excel',
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 5. 技术状态跟进措施表（41个业务字段） ───
DROP TABLE IF EXISTS ods_tech_state_measure_v2 CASCADE;
CREATE TABLE ods_tech_state_measure_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    tech_state_name          varchar(2000),  -- 技术状态项名称、代号
    change_item              varchar(2000),  -- 更改事项
    owner                    varchar(500),   -- 负责人
    dept                     varchar(500),   -- 责任科室（下拉选择）
    dept_leader              varchar(500),   -- 分管室领导
    change_submit_time       varchar(500),   -- 更改提出时间（格式：XXXX-XX-XX）
    change_submit_week       varchar(500),   -- 更改提出周数
    completion_signature     varchar(500),   -- 技术状态变更单是否完成签署（下拉选择）
    signature_closure_date   varchar(500),   -- 技术状态变更单闭环签署时间（格式：XXXX-XX-XX）
    signature_closure_week   varchar(500),   -- 技术状态变更单闭环签署周数
    change_reason            varchar(2000),  -- 简要更改原因及内容
    change_category          varchar(500),   -- 更改类别（下拉选择：I/II/III）
    plan_file_closure_date   varchar(500),   -- 计划完成文件闭环签署时间（格式：XXXX-XX-XX）
    plan_file_closure_week   varchar(500),   -- 计划完成文件闭环签署周数
    plan_reform_date         varchar(500),   -- 计划完成整改落实时间（格式：XXXX-XX-XX）
    plan_reform_week         varchar(500),   -- 计划完成整改落实周数
    plan_synced              varchar(500),   -- 计划是否已在进度信息汇总表中更新（下拉选择）
    new_plan_count           varchar(500),   -- 新增计划数量
    affected_files           varchar(2000),  -- 受影响文件
    affected_objects         varchar(2000),  -- 受影响实物（PBS名称）
    file_signature_status    varchar(500),   -- 文件签署状态（下拉选择）
    reform_status            varchar(500),   -- 整改落实状态（下拉选择）
    project_manager          varchar(500),   -- 项目主管
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
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 6. 风险信息汇总表（31个业务字段） ───
DROP TABLE IF EXISTS ods_risk_info_v2 CASCADE;
CREATE TABLE ods_risk_info_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    risk_name                varchar(2000),  -- 风险名称
    subsystem                varchar(500),   -- 所属分系统
    belonging_unit           varchar(500),   -- 所属单机
    risk_description         varchar(2000),  -- 风险描述（按条目梳理当前存在的主要具体风险点）
    risk_submit_time         varchar(500),   -- 风险提出时间（格式：XXXX-XX-XX）
    risk_submit_week         varchar(500),   -- 风险提出周数
    risk_phase               varchar(500),   -- 风险阶段（下拉选择）
    risk_category            varchar(500),   -- 风险分类（下拉选择）
    risk_level               varchar(500),   -- 风险等级（下拉选择：高/中/低）
    impact_scope             varchar(2000),  -- 影响域（关联影响分析，PBS产品层）
    response_measure         varchar(2000),  -- 风险应对措施
    final_release_time       varchar(500),   -- 最终风险释放时间
    final_release_week       varchar(500),   -- 最终风险释放周数
    monthly_control_plan     varchar(2000),  -- 风险管控月计划
    weekly_release_plan      varchar(2000),  -- 风险释放措施及周计划
    release_plan_synced      varchar(500),   -- 风险释放计划是否已在进度信息汇总表中更新（下拉选择）
    new_plan_count           varchar(500),   -- 新增计划数量
    progress_stat_time       varchar(500),   -- 进展统计时间（格式：XXXX-XX-XX）
    progress_stat_week       varchar(500),   -- 进展统计周数
    progress_situation       varchar(2000),  -- 进展情况
    response_owner           varchar(500),   -- 风险应对负责人（具体实施责任人）
    control_owner            varchar(500),   -- 风险防控负责人（总责任人，一般为分管副总）
    dept                     varchar(500),   -- 责任科室（下拉选择）
    risk_status              varchar(500),   -- 风险状态（下拉选择）
    risk_release_date        varchar(500),   -- 风险释放时间（格式：XXXX-XX-XX）
    risk_release_week        varchar(500),   -- 风险释放周数
    remark                   varchar(2000),  -- 备注
    last_update_time         varchar(500),   -- 最后更新时间（格式：XXXX-XX-XX）
    last_update_week         varchar(500),   -- 最后更新周数
    filled_by                varchar(500),   -- 填写人
    source_system            varchar(200) DEFAULT 'excel',
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 7. 风险跟进措施表（42个业务字段） ───
DROP TABLE IF EXISTS ods_risk_measure_v2 CASCADE;
CREATE TABLE ods_risk_measure_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    risk_name                varchar(2000),  -- 风险名称
    subsystem                varchar(500),   -- 所属分系统
    belonging_unit           varchar(500),   -- 所属单机
    risk_description         varchar(2000),  -- 风险描述
    risk_submit_time         varchar(500),   -- 风险提出时间（格式：XXXX-XX-XX）
    risk_submit_week         varchar(500),   -- 风险提出周数
    risk_phase               varchar(500),   -- 风险阶段（下拉选择）
    risk_category            varchar(500),   -- 风险分类（下拉选择）
    risk_level               varchar(500),   -- 风险等级（下拉选择：高/中/低）
    impact_scope             varchar(2000),  -- 影响域（PBS产品层）
    response_measure         varchar(2000),  -- 风险应对措施
    final_release_time       varchar(500),   -- 最终风险释放时间（格式：XXXX-XX-XX）
    monthly_control_plan     varchar(2000),  -- 风险管控月计划
    weekly_release_plan      varchar(2000),  -- 风险释放措施及周计划
    release_plan_synced      varchar(500),   -- 风险释放计划是否已在进度信息汇总表中更新（下拉选择）
    new_plan_count           varchar(500),   -- 新增计划数量
    progress_stat_time       varchar(500),   -- 进展统计时间（格式：XXXX-XX-XX）
    progress_stat_week       varchar(500),   -- 进展统计周数
    progress_situation       varchar(2000),  -- 进展情况
    response_owner           varchar(500),   -- 风险应对负责人（具体实施责任人）
    control_owner            varchar(500),   -- 风险防控负责人（总责任人，一般为分管副总）
    dept                     varchar(500),   -- 责任科室（下拉选择）
    risk_status              varchar(500),   -- 风险状态（下拉选择）
    project_manager          varchar(500),   -- 项目主管（下拉选择）
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
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);

-- ─── 8. 重要物料信息表（29个业务字段） ───
DROP TABLE IF EXISTS ods_material_info_v2 CASCADE;
CREATE TABLE ods_material_info_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目编号（下拉选择）
    subsystem                varchar(500),   -- 分系统/分任务
    pbs_no                   varchar(500),   -- PBS编号
    pbs_name                 varchar(500),   -- PBS名称
    self_or_outsource        varchar(500),   -- 自研或外协
    supplier_name            varchar(500),   -- 供应商名称
    is_long_cycle            varchar(500),   -- 是否长周期物料（下拉选择）
    contract_negotiation_date varchar(500),  -- 合同完成谈判时间（格式：XXXX-XX-XX）
    contract_negotiation_week varchar(500),  -- 合同完成谈判周数
    contract_delivery_date   varchar(500),   -- 合同要求到货时间（格式：XXXX-XX-XX）
    contract_delivery_week   varchar(500),   -- 合同要求到货周数
    actual_delivery_date     varchar(500),   -- 实际到货时间（格式：XXXX-XX-XX）
    actual_delivery_week     varchar(500),   -- 实际到货周数
    plan_inspect_date        varchar(500),   -- 计划检验时间（格式：XXXX-XX-XX）
    plan_inspect_week        varchar(500),   -- 计划检验周数
    complete_inspect_date    varchar(500),   -- 完成检验时间（格式：XXXX-XX-XX）
    complete_inspect_week    varchar(500),   -- 完成检验周数
    install_date             varchar(500),   -- 上装时间（格式：XXXX-XX-XX）
    install_week             varchar(500),   -- 上装周数
    dept_owner               varchar(500),   -- 责任科室/责任人
    control_dept_owner       varchar(500),   -- 管控责任部门/责任人
    weekly_progress          varchar(2000),  -- 本周进展
    affects_major_node       varchar(500),   -- 当前进展是否影响重要及以上节点
    risk_level               varchar(500),   -- 风险等级（下拉选择）
    risk_content             varchar(2000),  -- 主要风险内容及措施
    delay_impact             varchar(2000),  -- 延期影响分析
    last_update_time         varchar(500),   -- 最后更新时间（格式：XXXX-XX-XX）
    last_update_week         varchar(500),   -- 最后更新周数
    remark                   varchar(2000),  -- 备注
    source_system            varchar(200) DEFAULT 'excel',
    source_file              varchar(1000), -- 来源文件名/路径
    sheet_name               varchar(500),  -- 来源 sheet 名称
    batch_id                 varchar(500),  -- 导入批次 ID
    row_num                  varchar(500),  -- 来源行号（原始值）
    import_time              timestamp DEFAULT now()
);
