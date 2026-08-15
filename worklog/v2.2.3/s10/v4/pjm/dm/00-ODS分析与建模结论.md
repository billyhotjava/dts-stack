# ODS 分析与建模结论

## 1. ODS 目录结论

`ods_create_tables_v2.sql` 定义了 10 张项目管理 ODS 表。所有业务字段先以 `varchar` 原样落地，日期和数字在 STG 转型，枚举归一化、状态判断和派生口径在 DWD 完成。

目录内文件职责如下：

| 目录/文件 | 用途 | 建模时的使用方式 |
|---|---|---|
| `ods_create_tables_v2.sql` | PostgreSQL 10 张 ODS 的全量建表基线 | 用于字段和粒度分析；脚本会 `DROP ... CASCADE` 后重建，现场不得把它当成建模步骤执行 |
| `mysql/` | MySQL 5.7+/8.0+ 接入源模拟、黄金数据、校验、安全字段演进和回退 | 用于验证 JDBC/API 接入；源表不携带 `_dts_*`，落地元数据由 DTS 追加 |
| `ods-field-mapping/` | 10 张表的源字段到 ODS 字段映射 | 用于字段中文名、来源和转换规则复核，不替代实际源样本验证 |
| `ods-verify/` | 10 张表的逐表核对记录 | 与主 DDL、映射 CSV 和当前源样本三方复核；不盲信过期“待对齐”状态 |
| `upgrade/09_add_ods_budget_v2.sql` | 只新增预算 ODS 的幂等增量脚本 | 仅适用于现场已有其他 9 张表、只需补预算表的场景；执行仍需另行审批和备份 |
| `upgrade/11_add_budget_snapshot_date.sql` | 只新增预算业务快照日期的幂等增量脚本 | 结构迁移不自动回填业务时间；历史批次须经业务确认后单独处理 |

| 序号 | ODS 表 | 业务字段数 | 建议数据域 | 建议业务过程 | 当前 dbt 状态 |
|---|---|---:|---|---|---|
| 0 | `ods_project_subject_domain_v2` | 33 | 研究项目域 | 项目计划执行 | 已进入 STG→DWD→DWS→ADS |
| 1 | `ods_progress_measure_v2` | 23 | 研究项目域 | 项目跟进闭环 | 已进入 STG→DWD→DWS→ADS |
| 2 | `ods_quality_issue_v2` | 21 | 质量管理域 | 质量问题处置 | 已进入 STG→DWD→DWS→ADS |
| 3 | `ods_quality_measure_v2` | 33 | 质量管理域 | 质量措施闭环 | 已进入 STG→DWD→DWS→ADS |
| 4 | `ods_tech_state_v2` | 33 | 产品技术域 | 技术状态更改 | 已进入 STG→DWD→DWS→ADS |
| 5 | `ods_tech_state_measure_v2` | 41 | 产品技术域 | 技术状态措施闭环 | 已进入 STG→DWD→DWS→ADS |
| 6 | `ods_risk_info_v2` | 31 | 研究项目域 | 项目风险处置 | 已进入 STG→DWD→DWS→ADS |
| 7 | `ods_risk_measure_v2` | 42 | 研究项目域 | 风险措施闭环 | 已进入 STG→DWD→DWS→ADS |
| 8 | `ods_material_info_v2` | 29 | 物料供应域 | 重要物料交付跟踪 | 已进入 STG→DWD→DWS→ADS |
| 9 | `ods_budget_v2` | 9 | 财务管理域 | 预算执行快照 | 已进入 STG→DWD→DWS→ADS |

“业务字段数”按 PostgreSQL 主 DDL 注释口径记录，不包含代理 `id`、`_dts_*` 落地元数据，也不包含 MySQL 测试源另加的 `classification`/`owner_dept`。

“汇总表”是源 Excel 的名称，不代表它在数仓中应直接建为 DWS。例如 `ods_quality_issue_v2` 的一行仍是一条质量问题事实，因此目标是 DWD FACT。

## 2. 当前由源码确认的模型链

```text
10 张 ODS source
  → 10 张 STG view
  → 8 张 canonical 维度 + 10 张 alias 归一化辅助表
  → 10 张 DWD FACT
  → 10 张 DWS SUMMARY
  → 15 张 ADS APPLICATION
```

当前主链表如下：

| 层次 | 当前模型 |
|---|---|
| STG | 10 张 ODS 各有一张同主题 `stg_pm__*` 标准化视图 |
| DWD FACT | 原 5 张核心事实 + 项目跟进、质量措施、技术状态措施、风险措施、物料交付，共 10 张 |
| DWS | 原 5 张主题汇总 + 4 张措施闭环月汇总 + 1 张物料交付月汇总，共 10 张 |
| ADS | 原 10 张 KPI/派生应用 + 4 张措施闭环 KPI + 1 张物料交付 KPI，共 15 张 |

## 3. 分层职责

| 层 | 当前物化 | 在 DTS 中的处理方式 | 允许的职责 |
|---|---|---|---|
| ODS | 物理表 | 在规划中展示；登记为来源绑定，不在模型工作台重复建表 | 原样落地、技术追溯 |
| STG | `view` | 随 dbt 包导入的技术节点，不作为业务人员手工建模起点 | 空值收敛、字段改名、安全类型转换 |
| DWD | `table` | 维度表或明细表 | 枚举归一、维度键、业务状态、派生字段 |
| DWS | `table` | 汇总表 | 固定粒度的主题聚合，保留可重算的分子/分母 |
| ADS | `table` | 应用表 | 面向看板、接口和指标消费的输出契约 |

不要为了匹配目录结构重复创建自定义 ODS、STG、DWD、DWS、ADS 分层；优先复用系统内置分层。只有企业确有新的加工责任边界时才新建自定义分层。

## 4. 粒度与事实形态分析

| DWD 模型 | 业务粒度 | 当前实现键 | 建议事实形态 | 时间语义 |
|---|---|---|---|---|
| 项目节点 | 一个项目×分系统/分任务×节点任务×计划日期一行 | `node_id=project_node:source_row_id` | 累积快照 | 计划、实际、最后更新时间等里程碑日期 |
| 质量问题 | 一个项目下的一条质量问题一行 | `issue_id=quality_issue:source_row_id` | 累积快照 | 问题发生、归零完成、最后更新时间 |
| 技术状态 | 一个项目下的一项技术状态更改一行 | `tech_state_id=tech_state:source_row_id` | 累积快照 | 提出、签署、整改等里程碑日期 |
| 项目风险 | 一个项目下的一条风险一行 | `risk_id=risk_info:source_row_id` | 累积快照 | 提出、计划释放、实际释放、最后更新时间 |
| 预算明细 | 一个预算编号在一个业务快照日一行 | `snapshot_date + budget_no`，`budget_id` 由稳定业务粒度生成 | 周期快照 | 业务 `snapshot_date` |
| 项目跟进措施 | 一个项目节点的一条跟进措施一行 | 测试数据候选键 `project_no + node_task + measure_title + follow_up_date` | 累积快照 | 计划、跟进、闭环、最后更新时间 |
| 质量措施 | 一条质量问题跟进措施一行 | 测试数据候选键 `project_no + issue_name + measure_title + follow_up_date` | 累积快照 | 问题发生、跟进、闭环、最后更新时间 |
| 技术状态措施 | 一条技术状态跟进措施一行 | 测试数据候选键 `project_no + tech_state_name + measure_title + follow_up_date` | 累积快照 | 更改提出、跟进、闭环、最后更新时间 |
| 风险措施 | 一条项目风险跟进措施一行 | 测试数据候选键 `project_no + risk_name + measure_title + follow_up_date` | 累积快照 | 风险提出、跟进、闭环、最后更新时间 |
| 物料交付 | 一个项目下的一项 PBS 物料交付状态一行 | 测试数据候选键 `project_no + pbs_no` | 累积快照 | 合同交付、实际到货、检验、上装、最后更新时间 |

除预算外的源表普遍没有稳定业务 ID。当前核心事实仍保留来源行标识；新增措施/物料事实使用确定性候选自然键生成标识。现有测试数据已验证这些候选键非空且唯一，但单批 10 行样本不能证明生产数据长期唯一。进入增量、历史快照或跨批次对账前，仍必须由源系统提供稳定 ID，或由业务负责人基于生产画像冻结候选键。

预算表已增加业务 `snapshot_date`，预算链按该字段组织快照粒度。`_dts_import_time` 仍只是技术入湖时间，不得作为业务会计期间或快照日期。

## 5. 数值和枚举口径

- 预算金额单位为“万元”，不做单位换算。
- 已执行金额 = 预付账款 + 账面成本 + 应付账款。
- 剩余金额 = 预算金额 − 已执行金额。
- 超支金额 = `max(0, 已执行金额 − 预算金额)`。
- STG 保留 `*_raw` 原始枚举；DWD 通过 alias 表归一到 canonical 值，再通过 canonical 维度生成稳定 ID、中文标签和判断标志。
- 新建维度表时，使用 ASCII 稳定 `*_id` 作为主键。现有 dbt 的中文 `code` 是兼容字段，不应成为新模型的跨表主键。

## 6. 安全字段边界

MySQL 客户源模拟表另有：

- `classification`：源端安全分类，只能进入模型/资产的分类分级治理流程。
- `owner_dept`：源端责任部门编码，应由数据责任人确认其与业务字段 `dept`/`research_lab` 的关系。

禁止以下映射：

- `classification` → 业务分类；
- `classification` → 数据域；
- `classification` → 普通枚举维度；
- 未确认语义时，`owner_dept` → `dept`。

密级值、密级下限和权限证据必须由有权人员在治理流程中确认，操作手册不代填。

## 7. 当前必须显式登记的缺口

1. 除预算外的事实普遍缺少源系统稳定业务 ID；新增 5 张事实的候选键只通过当前测试样本，生产增量上线前仍需真实数据画像和责任人确认。
2. `model-governance.md` 要求 STG 保留 `source_file/source_sheet_name/source_batch_id/source_row_num`；当前 10 张 STG 会尽量透传已有落地元数据，但源表未提供的字段不能伪造，发布证据需注明来源能力边界。
3. 部分 `ods-verify` 文档仍标记“待对齐”，但主 DDL 已包含对应字段；实施时以主 DDL、字段映射 CSV 和源样本三方复核结果为准，并更新过期核对状态。
4. 当前 8 张 canonical 维度均是枚举/状态维度；项目、组织、人员等仍以文本属性出现。在未接入权威主数据、稳定编码和维护责任人前，不得为了“有维度表”而从业务文本去重生成伪主数据。
