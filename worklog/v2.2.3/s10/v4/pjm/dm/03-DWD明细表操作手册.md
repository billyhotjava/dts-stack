# DWD 明细表操作手册

## 1. 当前 DWD 创建范围

本批把 10 张已存在 dbt 实现的事实表登记为 DTS“明细表（FACT）”。其中 5 张是核心业务事实，另外 5 张覆盖措施闭环和重要物料交付。

| 模型名称 | 物理表名 | 数据域 | 业务过程 | 当前来源 |
|---|---|---|---|---|
| 项目节点明细 | `biz_dwd_project_node_v2` | 研究项目域 | 项目计划执行 | `ods_project_subject_domain_v2` 经 STG |
| 质量问题明细 | `biz_dwd_quality_issue_v2` | 质量管理域 | 质量问题处置 | `ods_quality_issue_v2` 经 STG |
| 技术状态更改明细 | `biz_dwd_tech_state_v2` | 产品技术域 | 技术状态更改 | `ods_tech_state_v2` 经 STG |
| 项目风险明细 | `biz_dwd_risk_info_v2` | 研究项目域 | 项目风险处置 | `ods_risk_info_v2` 经 STG |
| 预算执行明细 | `biz_dwd_budget_v2` | 财务管理域 | 预算执行快照 | `ods_budget_v2` 经 STG |
| 项目跟进措施明细 | `biz_dwd_project_follow_up_v2` | 研究项目域 | 项目跟进闭环 | `ods_progress_measure_v2` 经 STG |
| 质量措施明细 | `biz_dwd_quality_measure_v2` | 质量管理域 | 质量措施闭环 | `ods_quality_measure_v2` 经 STG |
| 技术状态措施明细 | `biz_dwd_tech_state_measure_v2` | 产品技术域 | 技术状态措施闭环 | `ods_tech_state_measure_v2` 经 STG |
| 风险措施明细 | `biz_dwd_risk_measure_v2` | 研究项目域 | 风险措施闭环 | `ods_risk_measure_v2` 经 STG |
| 重要物料交付明细 | `biz_dwd_material_delivery_v2` | 物料供应域 | 重要物料交付跟踪 | `ods_material_info_v2` 经 STG |

## 2. 通用创建步骤

推荐先通过逆向建模导入 dbt 包，再在模型工作台核对或补录。若仅登记逻辑草稿，执行：

1. 进入“数据建模 → 模型工作台”。
2. 点击新建，选择“明细表”。
3. 选择数据域。研究项目域有多个有效过程时，必须选择正确业务过程。
4. 选择系统 DWD 分层。
5. 填写模型名称、业务定义和模型粒度。
6. 物理表名、物化方式、加载策略和分区字段按逐表清单核对。
7. 逐行录入字段技术名、类型、中文显示名、字段作用和空值规则。
8. 至少一个字段标为 KEY；粒度键必须唯一对应 KEY 字段。
9. 保存草稿后补齐固定来源、维度引用和实现。
10. 点击“提交”，只检查 DESIGNED 逻辑设计；发布门禁另行检查。

当前普通模型新建页没有来源/上游关系编辑器。如果为全新草稿直接填写物理表名，保存可能提示“请先配置数据实现来源”。本项目已有 dbt 实现，正确入口是逆向导入，不要用空来源绕过门禁。

FACT 的 `factShape` 和 `timeSemantics` 是服务端设计门禁必需项，但当前基础表单未提供编辑控件。逆向包或服务端登记必须携带它们；若导入结果没有这些语义，FACT 只能停留在草稿，不能标记 DESIGNED。

## 3. 项目节点明细

### 3.1 基本信息

| 页面字段 | 填写值 |
|---|---|
| 数据域 | 研究项目域 |
| 业务过程 | 项目计划执行 |
| 模型类型 | 明细表 |
| 数仓分层 | DWD |
| 模型名称 | 项目节点明细 |
| 物理表名 | `biz_dwd_project_node_v2` |
| 业务定义 | 统一记录项目任务节点的计划、实际完成、风险和责任状态。 |
| 模型粒度 | 一个项目×分系统/分任务×节点任务×计划日期一行。 |
| 物化方式 | `table` |
| 加载策略 | 全量 |
| 分区字段 | 留空；当前 dbt 未分区 |
| 事实形态 | `ACCUMULATING_SNAPSHOT`（累积快照） |
| 时间语义 | `MILESTONE_DATES`：计划开始、计划完成、实际开始、实际完成、最后更新 |

### 3.2 字段角色检查

| 作用 | 字段 |
|---|---|
| KEY | `node_id` |
| 技术追溯 ATTRIBUTE | `source_row_id`、`source_table`、`source_system`、`source_imported_at` |
| 业务 ATTRIBUTE | `project_no`、`subsystem`、`node_task`、`owner`、`dept`、`dept_leader`、`collab_dept`、`supervisor_dept`、`incomplete_reason`、`risk_content`、`delay_impact`、`institute_leader`、`project_manager`、`filled_by`、`highlight`、`deliverable`、`data_source` |
| 状态/维度 ATTRIBUTE | 完成状态、节点类型、风险等级的 raw/code/id/label 字段，所有 `is_*` 状态标志，`delay_applied_raw`、`delay_applied` |
| TIME | `plan_start_date`、`plan_date`、`actual_start_date`、`actual_date`、`delay_expected_date`、`original_plan_date`、`last_update_time`、`as_of_date`；年月/季度字段作为时间分组属性 |
| MEASURE | `delay_days`；周数字段可按当前指标使用设为 MEASURE 或时间属性，但同一规划内必须一致 |
| ETL ATTRIBUTE | `etl_time` |

必测字段：`node_id` unique/not_null，`project_no` not_null，`plan_date`/`plan_month` not_null，完成状态/节点类型/风险等级 accepted values。

当前 `node_id` 来源于落地行 ID。发布前登记“稳定业务键待确认”风险；在稳定键补齐前只采用全量重建，不做增量合并。

## 4. 质量问题明细

### 4.1 基本信息

| 页面字段 | 填写值 |
|---|---|
| 数据域 / 业务过程 | 质量管理域 / 质量问题处置 |
| 模型名称 / 物理表名 | 质量问题明细 / `biz_dwd_quality_issue_v2` |
| 业务定义 | 统一记录质量问题的发生、原因、归零计划、当前进展和归零结果。 |
| 模型粒度 | 一个项目下的一条质量问题一行。 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |
| 事实形态 | `ACCUMULATING_SNAPSHOT` |
| 时间语义 | `MILESTONE_DATES`：问题发生、归零完成、最后更新 |

### 4.2 字段角色检查

| 作用 | 字段 |
|---|---|
| KEY | `issue_id` |
| 技术追溯 ATTRIBUTE | `source_row_id`、`source_table`、`source_system`、`source_imported_at` |
| 业务 ATTRIBUTE | `project_no`、`subsystem`、`issue_name`、`dept`、`team_leader`、`dept_leader`、`issue_summary`、`zero_plan`、`current_progress`、`project_manager`、`filled_by` |
| 状态/维度 ATTRIBUTE | 问题分类和归零状态的 raw/code/id/label、`zero_plan_synced*`、所有 `is_*`/`cat_*`、`has_zero_plan` |
| TIME | `issue_date`、`zero_complete_date`、`last_update_time`、`state_as_of_date` 及年月/季度字段 |
| MEASURE | `new_plan_count`、`pending_days`、`aging_days` |
| ETL ATTRIBUTE | `etl_time` |

必测字段：`issue_id` unique/not_null，`project_no`/`issue_date`/`issue_month` not_null，状态和原因分类 accepted values。

候选自然键 `project_no + subsystem + issue_name + issue_date` 尚未证明唯一；源端应补质量问题编号。

## 5. 技术状态更改明细

### 5.1 基本信息

| 页面字段 | 填写值 |
|---|---|
| 数据域 / 业务过程 | 产品技术域 / 技术状态更改 |
| 模型名称 / 物理表名 | 技术状态更改明细 / `biz_dwd_tech_state_v2` |
| 业务定义 | 统一记录技术状态更改从提出、评审、签署到整改落实的全过程状态。 |
| 模型粒度 | 一个项目下的一项技术状态更改一行。 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |
| 事实形态 | `ACCUMULATING_SNAPSHOT` |
| 时间语义 | `MILESTONE_DATES`：提出、变更单签署、文件签署、整改、最后更新 |

### 5.2 字段角色检查

| 作用 | 字段 |
|---|---|
| KEY | `tech_state_id` |
| 技术追溯 ATTRIBUTE | `source_row_id`、`source_table`、`source_system`、`source_imported_at` |
| 业务 ATTRIBUTE | `project_no`、`tech_state_name`、`change_item`、`owner`、`dept`、`dept_leader`、`change_reason`、`affected_files`、`affected_objects`、`project_manager`、`filled_by`、`remark` |
| 状态/维度 ATTRIBUTE | 更改类别、文件签署、整改、计划同步、完成签署的 raw/code/id/label/context 及 `is_*` 标志 |
| TIME | `change_submit_time`、`signature_closure_date`、`plan_file_closure_date`、`plan_reform_date`、`file_signature_date`、`reform_date`、`last_update_time`、`state_as_of_date` 及月份/年份字段 |
| MEASURE | `new_plan_count`；周数字段按统一规则处理 |
| ETL ATTRIBUTE | `etl_time` |

必测字段：`tech_state_id` unique/not_null，`project_no`/`change_submit_time`/`submit_month` not_null，更改类别 accepted values。

候选自然键 `project_no + tech_state_name + change_item + change_submit_time` 尚未证明唯一；源端应补技术更改单编号。

## 6. 项目风险明细

### 6.1 基本信息

| 页面字段 | 填写值 |
|---|---|
| 数据域 / 业务过程 | 研究项目域 / 项目风险处置 |
| 模型名称 / 物理表名 | 项目风险明细 / `biz_dwd_risk_info_v2` |
| 业务定义 | 统一记录项目风险的识别、分级、应对、跟踪和释放状态。 |
| 模型粒度 | 一个项目下的一条风险一行。 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |
| 事实形态 | `ACCUMULATING_SNAPSHOT` |
| 时间语义 | `MILESTONE_DATES`：提出、目标释放、进展统计、实际释放、最后更新 |

### 6.2 字段角色检查

| 作用 | 字段 |
|---|---|
| KEY | `risk_id` |
| 技术追溯 ATTRIBUTE | `source_row_id`、`source_table`、`source_system`、`source_imported_at` |
| 业务 ATTRIBUTE | `project_no`、`risk_name`、`subsystem`、`belonging_unit`、`risk_description`、`risk_phase`、`impact_scope`、`response_measure`、`monthly_control_plan`、`weekly_release_plan`、`progress_situation`、`response_owner`、`control_owner`、`dept`、`remark`、`filled_by` |
| 状态/维度 ATTRIBUTE | 风险分类、风险等级、释放计划同步和风险状态的 raw/code/id/label/rank、所有 `is_*` 标志 |
| TIME | `risk_submit_date`、`final_release_date`、`progress_stat_date`、`risk_release_date`、`last_update_time`、`state_as_of_date` 及年月/季度字段 |
| MEASURE | `new_plan_count`、`pending_days`；周数字段按统一规则处理 |
| ETL ATTRIBUTE | `etl_time` |

必测字段：`risk_id` unique/not_null，`project_no`/`risk_submit_date`/`submit_month` not_null，风险等级和分类 accepted values。

候选自然键 `project_no + risk_name + risk_submit_date` 尚未证明唯一；源端应补风险编号。

## 7. 预算执行明细

### 7.1 基本信息

| 页面字段 | 填写值 |
|---|---|
| 数据域 / 业务过程 | 财务管理域 / 预算执行快照 |
| 模型名称 / 物理表名 | 预算执行明细 / `biz_dwd_budget_v2` |
| 业务定义 | 统一记录每个预算编号当前预算、预付、账面成本、应付、已执行、剩余和超支状态，金额单位万元。 |
| 模型粒度 | 一个预算编号在一个业务快照日一行。 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |
| 事实形态 | 目标为 `PERIODIC_SNAPSHOT` |
| 时间语义 | `SNAPSHOT_DATE`，绑定业务字段 `snapshot_date` |

### 7.2 字段角色检查

| 作用 | 字段 |
|---|---|
| KEY | `budget_no`；`budget_id` 保留为现有实现代理标识但不作为长期业务键 |
| 技术追溯 ATTRIBUTE | `source_row_id`、`source_table`、`source_system`、`source_imported_at` |
| 业务 ATTRIBUTE | `project_no`、`subtopic`、`research_lab` |
| MEASURE | `budget_amount`、`prepaid_amount`、`book_cost_amount`、`payable_amount`、`executed_amount`、`remaining_amount`、`overrun_amount`、`execution_rate_line` |
| 状态 ATTRIBUTE | `is_overrun` |
| TIME | `snapshot_date`；`source_imported_at` 仅为技术追溯，不充当业务 TIME |
| ETL ATTRIBUTE | `etl_time` |

必测字段：`budget_no` unique/not_null，`budget_id` unique/not_null，`project_no` not_null；金额口径与单位复核。

`budget_no` 只在单个快照内唯一；跨期粒度和去重必须使用 `snapshot_date + budget_no`。`budget_id` 也应由这两个稳定业务字段生成，不再依赖易变的导入行 ID。

## 8. 措施闭环和物料交付明细

| 物理表名 | 粒度/KEY | 事实形态 | 主要 TIME | 主要 MEASURE |
|---|---|---|---|---|
| `biz_dwd_project_follow_up_v2` | 一条项目节点跟进措施；`measure_id` | `ACCUMULATING_SNAPSHOT` | `plan_date`、`follow_up_date`、`final_closure_date`、`last_update_time` | `closure_days`、`pending_days` |
| `biz_dwd_quality_measure_v2` | 一条质量问题跟进措施；`measure_id` | `ACCUMULATING_SNAPSHOT` | `issue_date`、`follow_up_date`、`final_closure_date`、`last_update_time` | `new_plan_count`、`closure_days`、`pending_days` |
| `biz_dwd_tech_state_measure_v2` | 一条技术状态跟进措施；`measure_id` | `ACCUMULATING_SNAPSHOT` | `change_submit_date`、`follow_up_date`、`final_closure_date`、`last_update_time` | `new_plan_count`、`closure_days`、`pending_days` |
| `biz_dwd_risk_measure_v2` | 一条风险跟进措施；`measure_id` | `ACCUMULATING_SNAPSHOT` | `risk_submit_date`、`follow_up_date`、`final_closure_date`、`last_update_time` | `new_plan_count`、`closure_days`、`pending_days` |
| `biz_dwd_material_delivery_v2` | 一个项目下一项 PBS 物料；`material_delivery_id` | `ACCUMULATING_SNAPSHOT` | 合同交付、实际到货、检验、上装、最后更新时间 | `delivery_delay_days` |

这 5 张表的标识由候选自然键确定性生成，当前测试数据已验证非空且无重复；这只支持测试环境全量物化。生产增量前必须重新画像真实数据，确认跨批次稳定性及物料多供应商/多批次场景，否则应先补源业务 ID。

## 9. DWD 提交前总检查

- [ ] 每张 FACT 绑定同一数据域内的真实业务过程稳定 ID。
- [ ] 粒度说明回答“一行是什么”，KEY 字段与粒度键一一对应。
- [ ] `factShape` 与 `timeSemantics` 已随导入语义或服务端事实保存。
- [ ] 所有 TIME 语义字段在字段表中角色为 TIME。
- [ ] 来源绑定固定到当前 ODS source 版本；dbt 内部加工只从 STG 进入 DWD。
- [ ] canonical 维度引用固定修订，alias 映射未被当作业务维度。
- [ ] 每个技术字段名为小写英文/数字/下划线，每个字段都有中文显示名。
- [ ] `classification` 不作为普通业务字段偷偷丢入 FACT；分类分级走独立治理门禁。
- [ ] 源业务 ID/候选自然键风险已登记；预算快照日期已使用业务字段，不再以技术导入时间替代。
