# ODS 表字段映射说明

## 重要提示

**Excel 导入入湖任务时，ODS 目标表的字段名必须和本文档一致。** 模型 SQL 按字段名引用 ODS 表，如果字段名不匹配，dbt run / SQL 建表会报错。

所有字段类型选 varchar/text — 类型转换由 dbt DWD 层处理。

---

## 1. ods_project_subject_domain — 项目主体域原始数据（执行域）

| 序号 | Excel 表头（project1.xlsx） | ODS 字段名 | 数据类型 | 模型引用 | 说明 |
|:---:|---------------------------|-----------|---------|:------:|------|
| 1 | 项目编号 | `project_no` | varchar | ✓ | 主键之一，不能为空 |
| 2 | 分系统/分任务 | `subsystem` | varchar | ✓ | 子项目标识，不能为空 |
| 3 | 节点任务及目标 | `node_task` | varchar | ✓ | 主键之一 |
| 4 | 节点计划时间 | `plan_date` | varchar | ✓ | 主键之一，模型用 parse_date_safe 转日期 |
| 5 | 节点计划周数 | `plan_week` | varchar | ✓ | 模型用 parse_numeric_safe 转数值 |
| 6 | 交付物 | `deliverable` | varchar | ✓ | 描述性字段 |
| 7 | 节点类型 | `node_type` | varchar | ✓ | 枚举：一般节点/重要节点/重大节点/里程碑节点 |
| 8 | 负责人 | `owner` | varchar | ✓ | |
| 9 | 责任科室 | `dept` | varchar | ✓ | |
| 10 | 分管室领导 | `dept_leader` | varchar | ✓ | |
| 11 | 完成情况 | `completion_status` | varchar | ✓ | 枚举：6种完成状态 |
| 12 | 协同部门 | `collab_dept` | varchar | ✓ | |
| 13 | 责任监管部门 | `supervisor_dept` | varchar | ✓ | |
| 14 | 延期预计完成时间 | `delay_expected_date` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 15 | 未完成原因及当前进展 | `incomplete_reason` | varchar | ✓ | 用于延期原因自动分类 |
| 16 | 风险等级 | `risk_level` | varchar | ✓ | 枚举：高/中/低 |
| 17 | 主要风险内容及措施 | `risk_content` | varchar | ✓ | |
| 18 | 延期影响分析 | `delay_impact` | varchar | ✓ | |
| 19 | 实际完成时间 | `actual_date` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 20 | 实际完成周数 | `actual_week` | varchar | ✓ | 模型用 parse_numeric_safe 转数值 |
| 21 | 所领导 | `institute_leader` | varchar | ✓ | |
| 22 | 来源 | `source` | varchar | ✓ | |
| 23 | 延期项目原计划时间 | `original_plan_date` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 24 | 计划延误时间（已变更） | `delay_days_changed` | varchar | | 透传，不参与计算 |
| 25 | 计划延误时间（未变更） | `delay_days_unchanged` | varchar | | 透传，不参与计算 |
| 26 | 是否提交延期申请 | `delay_applied` | varchar | ✓ | |
| 27 | 项目主管 | `project_manager` | varchar | ✓ | |
| 28 | 最后更新时间 | `last_update_time` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 29 | 最后更新周数 | `last_update_week` | varchar | ✓ | 模型用 parse_numeric_safe 转数值 |
| 30 | 填写人 | `filled_by` | varchar | ✓ | |
| 31 | 亮点工作 | `highlight` | varchar | ✓ | |

### 完成情况枚举值（必须严格匹配）

| 完成情况 | is_completed | is_on_time | is_overdue_completed | is_incomplete |
|---------|:---:|:---:|:---:|:---:|
| 正常待完成 | ✗ | ✗ | ✗ | ✗ |
| 按时完成 | ✓ | ✓ | ✗ | ✗ |
| 超期已完成已变更 | ✓ | ✗ | ✓ | ✗ |
| 超期已完成未变更 | ✓ | ✗ | ✓ | ✗ |
| 不正常待变更 | ✗ | ✗ | ✗ | ✓ |
| 超期未完成未变更 | ✗ | ✗ | ✗ | ✓ |
| 超期未完成已变更 | ✗ | ✗ | ✗ | ✓ |

### 节点类型枚举值

| 节点类型 | is_general |
|---------|:---:|
| 一般节点 | ✓ |
| 重要节点 | ✗ |
| 重大节点 | ✗ |
| 里程碑节点 | ✗ |

---

## 2. ods_quality_issue — 质量信息汇总表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 主键之一，不能为空 |
| 2 | 问题名称/问题描述 | `issue_name` | 主键之一 |
| 3 | 原因分类 | `issue_category` | 枚举：设计/工艺/管理/元器件/操作/外协外购/软件/其他 |
| 4 | 质量问题发生时间 | `issue_date` | 主键之一，格式 YYYY-MM-DD |
| 5 | 状态 | `status` | 枚举：未完成归零/已完成技术归零/已完成管理归零/已完成技术和管理归零 |
| 6 | 闭环状态 | `closure_status` | 枚举：已闭环/未闭环 |
| 7 | 归零计划 | `zero_plan` | 有/无 |
| 8 | 责任科室 | `dept` | |
| 9 | 分系统 | `subsystem` | |
| 10 | 负责人 | `owner` | |
| 11 | 最后更新时间 | `last_update_time` | |
| 12 | 填写人 | `filled_by` | |
| 13 | 备注 | `remark` | |

### 状态枚举值

| 状态 | is_zero_completed | is_tech_zero | is_mgmt_zero | is_both_zero |
|------|:-:|:-:|:-:|:-:|
| 未完成归零 | ✗ | ✗ | ✗ | ✗ |
| 已完成技术归零 | ✓ | ✓ | ✗ | ✗ |
| 已完成管理归零 | ✓ | ✗ | ✓ | ✗ |
| 已完成技术和管理归零 | ✓ | ✓ | ✓ | ✓ |

---

## 3. ods_quality_measure — 质量跟进措施表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 不能为空 |
| 2 | 问题名称 | `issue_name` | 关联到质量信息汇总表 |
| 3 | 措施内容 | `measure_content` | |
| 4 | 措施状态 | `measure_status` | |
| 5 | 责任人 | `responsible_person` | |
| 6 | 计划完成时间 | `deadline` | |
| 7 | 实际完成时间 | `actual_complete_date` | |
| 8 | 最后更新时间 | `last_update_time` | |
| 9 | 填写人 | `filled_by` | |
| 10 | 备注 | `remark` | |

---

## 4. ods_tech_state — 技术状态信息汇总表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 主键之一，不能为空 |
| 2 | 技术状态名称/代号 | `tech_state_name` | 主键之一 |
| 3 | 更改事项 | `change_item` | 主键之一 |
| 4 | 更改类别 | `change_category` | 枚举：I/II/III |
| 5 | 更改提出时间 | `change_submit_time` | 格式 YYYY-MM-DD |
| 6 | 文件签署状态 | `file_signature_status` | 见签署状态枚举 |
| 7 | 是否完成签署 | `completion_signature` | 是/否 |
| 8 | 整改落实状态 | `reform_status` | 枚举：未落实整改/已落实整改/不涉及整改 |
| 9 | 闭环状态 | `closure_status` | 已闭环/未闭环 |
| 10 | 责任科室 | `dept` | |
| 11 | 分系统 | `subsystem` | |
| 12 | 最后更新时间 | `last_update_time` | |
| 13 | 填写人 | `filled_by` | |
| 14 | 备注 | `remark` | |

### 更改类别枚举值

| 更改类别 | severity_rank |
|---------|:-:|
| I | 3 |
| II | 2 |
| III | 1 |

---

## 5. ods_tech_state_measure — 技术状态跟进措施表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 不能为空 |
| 2 | 技术状态名称 | `tech_state_name` | 关联到技术状态汇总表 |
| 3 | 措施内容 | `measure_content` | |
| 4 | 措施状态 | `measure_status` | |
| 5 | 责任人 | `responsible_person` | |
| 6 | 计划完成时间 | `deadline` | |
| 7 | 实际完成时间 | `actual_complete_date` | |
| 8 | 最后更新时间 | `last_update_time` | |
| 9 | 填写人 | `filled_by` | |
| 10 | 备注 | `remark` | |

---

## 6. ods_risk_info — 风险信息汇总表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 主键之一，不能为空 |
| 2 | 风险名称 | `risk_name` | 主键之一 |
| 3 | 风险等级 | `risk_level` | 枚举：高/中/低 |
| 4 | 风险提出时间 | `risk_submit_time` | 主键之一，格式 YYYY-MM-DD |
| 5 | 主要风险内容 | `risk_content` | |
| 6 | 影响范围 | `impact_scope` | |
| 7 | 闭环状态 | `closure_status` | 已闭环/未闭环 |
| 8 | 应对措施 | `response_measure` | |
| 9 | 责任科室 | `dept` | |
| 10 | 分系统 | `subsystem` | |
| 11 | 负责人 | `owner` | |
| 12 | 最后更新时间 | `last_update_time` | |
| 13 | 填写人 | `filled_by` | |
| 14 | 备注 | `remark` | |

---

## 7. ods_risk_measure — 风险跟进措施表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 不能为空 |
| 2 | 风险名称 | `risk_name` | 关联到风险信息汇总表 |
| 3 | 措施内容 | `measure_content` | |
| 4 | 措施状态 | `measure_status` | |
| 5 | 责任人 | `responsible_person` | |
| 6 | 计划完成时间 | `deadline` | |
| 7 | 实际完成时间 | `actual_complete_date` | |
| 8 | 闭环交付物 | `closure_deliverable` | |
| 9 | 最后更新时间 | `last_update_time` | |
| 10 | 填写人 | `filled_by` | |
| 11 | 备注 | `remark` | |

---

## 8. ods_cost_accounting — 成本核算基本表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 主键之一，不能为空 |
| 2 | 项目名称 | `project_name` | |
| 3 | 统计周期 | `accounting_period` | 主键之一，格式 YYYY 或 YYYY-MM |
| 4 | 预算金额 | `budget_amount` | 万元 |
| 5 | 实际金额 | `actual_amount` | 万元 |
| 6 | 责任科室/部门 | `dept` | |
| 7 | 费用类别 | `cost_category` | 可选 |
| 8 | 备注 | `remark` | |

---

## 脏数据处理

以下占位值会被 `nullif_placeholder` 宏自动转为 NULL：

`''`, `'/'`, `'-'`, `'--'`, `'N/A'`, `'NA'`, `'#N/A'`, `'#VALUE!'`, `'#DIV/0!'`, `'NULL'`

## 必填字段

以下字段为空时，该行会被模型过滤（不进入 DWD 层）：

- 执行域：`project_no`（项目编号）+ `plan_date`（节点计划时间）
- 其他域：`project_no`（项目编号）

## 数据流转

```
Excel 原始数据（各表格式）
  ↓ Addax 搬运（全 varchar，不做类型转换）
ODS 表 (8张): ods_project_subject_domain / ods_quality_issue / ods_tech_state / ...
  ↓ dbt 清洗（nullif_placeholder + parse_date_safe + 枚举标准化）
DWD 表 (20张): 维度表 + 事实表
  ↓ 周期聚合
DWS 表 (8张): 按 year/month/project/dept 汇总
  ↓ KPI 计算
ADS 表 (11张): 按 year/month 输出最终指标
  ↓ 大屏消费
查询卡片 / GPMC 看板 API
```
