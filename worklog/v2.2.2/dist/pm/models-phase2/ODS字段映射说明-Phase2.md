# ODS 字段映射说明（Phase 2 — 质量/技术状态/风险/成本）

## 重要提示

**Excel 导入入湖任务时，ODS 目标表的字段名必须和本文档一致。** 模型 SQL 按字段名引用 ODS 表，如果字段名不匹配，dbt run 会报错。

所有字段类型选 varchar/text — 类型转换由 dbt DWD 层处理。

---

## 1. ods_quality_issue — 质量信息汇总表

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

### 状态枚举值（必须严格匹配）

| 状态 | is_zero_completed | is_tech_zero | is_mgmt_zero | is_both_zero |
|------|:-:|:-:|:-:|:-:|
| 未完成归零 | ✗ | ✗ | ✗ | ✗ |
| 已完成技术归零 | ✓ | ✓ | ✗ | ✗ |
| 已完成管理归零 | ✓ | ✗ | ✓ | ✗ |
| 已完成技术和管理归零 | ✓ | ✓ | ✓ | ✓ |

---

## 2. ods_quality_measure — 质量跟进措施表

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

## 3. ods_tech_state — 技术状态信息汇总表

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 主键之一，不能为空 |
| 2 | 技术状态名称/代号 | `tech_state_name` | 主键之一 |
| 3 | 更改事项 | `change_item` | 主键之一 |
| 4 | 更改类别 | `change_category` | 枚举：I/II/III（或 Ⅰ/Ⅱ/Ⅲ） |
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
| I (Ⅰ) | 3 |
| II (Ⅱ) | 2 |
| III (Ⅲ) | 1 |

---

## 4. ods_tech_state_measure — 技术状态跟进措施表

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

## 5. ods_risk_info — 风险信息汇总表

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

## 6. ods_risk_measure — 风险跟进措施表

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

## 7. ods_cost_accounting — 成本核算基本表

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

## 8. ods_project_info — 项目信息表（主数据）

| 序号 | Excel 表头 | ODS 字段名 | 说明 |
|:---:|-----------|-----------|------|
| 1 | 项目编号 | `project_no` | 主键，不能为空 |
| 2 | 项目名称 | `project_name` | |
| 3 | 项目类型 | `project_type` | |
| 4 | 项目级别 | `project_level` | |
| 5 | 责任科室 | `dept` | |
| 6 | 事业部 | `business_unit` | |
| 7 | 项目主管 | `project_manager` | |
| 8 | 分管室领导 | `dept_leader` | |
| 9 | 所领导 | `institute_leader` | |
| 10 | 项目开始时间 | `start_date` | |
| 11 | 计划结束时间 | `plan_end_date` | |
| 12 | 项目状态 | `status` | 枚举：策划中/执行中/验收中/已完成/已暂停 |
| 13 | 备注 | `remark` | |

---

## 脏数据处理

以下占位值会被 `nullif_placeholder` 宏自动转为 NULL：

`''`, `'/'`, `'-'`, `'--'`, `'N/A'`, `'NA'`, `'#N/A'`, `'#VALUE!'`, `'#DIV/0!'`, `'NULL'`

## 必填字段

以下字段为空时，该行会被模型过滤（不进入 DWD 层）：

- 所有表：`project_no`（项目编号）

## 数据流转

```
Excel 原始数据（各表格式）
  ↓ Addax 搬运（全 varchar，不做类型转换）
ODS 表: ods_quality_issue / ods_tech_state / ods_risk_info / ods_cost_accounting / ...
  ↓ dbt 清洗（nullif_placeholder + parse_date_safe + 枚举标准化）
DWD 表: biz_dwd_quality_issue / biz_dwd_tech_state / biz_dwd_risk_info / biz_dwd_cost_accounting
  ↓ 周期聚合
DWS 表: biz_dws_*_period_summary（按 year/month/project/dept 汇总）
  ↓ KPI 计算
ADS 表: biz_ads_*_kpi（按 year/month 输出最终指标）
  ↓ Java 运行时消费
ProjectCockpitService → GPMC 看板 API
```
