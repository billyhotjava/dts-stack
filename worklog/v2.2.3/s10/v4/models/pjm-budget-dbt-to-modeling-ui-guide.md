# PJM 预算 dbt 模型转建模 UI 手工录入指南

本文把现有 `pm_analytics_v3` dbt 项目中的预算模型，转换成当前“模型中心”三阶段页面可填写的内容，供人工验证页面逻辑使用。

- dbt 项目目录：`worklog/v2.2.3/s10/v4/pjm/dbt_model`
- 本次主链：`public.ods_budget_v2 → biz_dwd_budget_v2 → biz_dws_budget_v2 → biz_ads_budget_kpi_v2`
- 可选延伸：`biz_ads_budget_derived_v2`
- 独立维度示例：`dim_node_type_v2`，它不是预算主链的依赖

> 重要：按 2026-07-25 当前源码，新建抽屉与后端创建契约仍有阻断，DIMENSION 保存也有字段契约冲突。第 2 节给出了识别方法。若第一步“保存草稿”失败，不要反复提交；这不是输入内容错误。

## 1. 先理解 dbt 与当前 UI 的对应关系

```text
dbt source / 已有 ODS 表
  public.ods_budget_v2
          │
          │  当前计划“来源盘点”中确认
          ▼
dbt STG
  stg_pm__budget_v2
          │
          │  普通模式下由系统作为临时技术节点处理
          │  不在“模型中心”单独创建 ModelSpec
          ▼
DWD dbt model                UI：明细表 FACT
  biz_dwd_budget_v2
          ▼
DWS dbt model                UI：汇总表 SUMMARY
  biz_dws_budget_v2
          ▼
ADS dbt model                UI：应用表 APPLICATION
  biz_ads_budget_kpi_v2
          ▼
ADS derived dbt model        UI：应用表 APPLICATION（可选）
  biz_ads_budget_derived_v2
```

| dbt 概念 | 当前 UI 中的含义 |
|---|---|
| `source('pm_ods_v2', 'budget_v2')` | 当前计划中已确认的物理来源 `public.ods_budget_v2` |
| `stg_pm__budget_v2` | 普通模式的临时 STG 技术节点，不创建四类表模型 |
| DWD 业务明细模型 | 明细表（FACT），目标层由系统固定为 DWD |
| DWS 汇总模型 | 汇总表（SUMMARY），目标层由系统固定为 DWS |
| ADS 消费模型 | 应用表（APPLICATION），目标层由系统固定为 ADS |
| `ref()` | 在“上游逻辑模型”中选择并锁定上游 revision |
| `materialized='table'` | “数据实现 → 目标物化方式”选择“表” |
| schema.yml 字段测试 | 后续编译/测试证据；不是来源选择 |

## 2. 开始前检查与当前阻断

### 2.1 页面前置条件

1. 在“建模工作台”选择一个可编辑的建设计划。
2. 当前计划中应有一个已确认的“项目管理”业务分类。名称可以不同，但整个测试必须使用同一个分类。
3. 在“规划基线 → 来源盘点”中确认 `public.ods_budget_v2`：
   - 连接测试成功只代表可访问。
   - 必须先完成元数据同步，再把具体表加入当前计划并确认。
   - 页面中的来源绑定 ID、版本和技术标识均由系统带出，不手工输入。
4. 依次创建 FACT、SUMMARY、APPLICATION。下游模型只能选择已经保存且当前有效的上游模型。

ODS 源表应至少能看到以下字段：

| 字段 | 源类型 | 含义 |
|---|---|---|
| `id` | serial / bigint | ODS 行主键 |
| `project_no` | varchar | 项目号 |
| `budget_no` | varchar | 预算编号 |
| `subtopic` | varchar | 子课题 |
| `research_lab` | varchar | 研究室 |
| `budget_amount_adjusted` | varchar | 调整后预算 |
| `prepaid_amount` | varchar | 预付账款 |
| `book_cost_amount` | varchar | 账面成本 |
| `payable_amount` | varchar | 应付账款 |
| `_dts_source_system` | varchar | 来源系统 |
| `_dts_import_time` | timestamp | 导入时间 |

源定义参考：[pm_sources_v2.yml](../pjm/dbt_model/models/pm_sources_v2.yml)。

### 2.2 当前源码的两个确定阻断

1. “新建模型”抽屉只收集建设计划、业务分类、模型类型、模型名称和用途说明；后端创建接口却立即按完整模型校验粒度、字段和依赖。由当前源码构建的版本可能在“保存草稿”时返回 422。
2. DIMENSION 逻辑页会发送 `dimensionCode`、`reuseScope`，当前后端 `dimensionProfile` 写入契约只接受 `hierarchies`、`scdPolicy`，维度保存可能返回 422。

人工测试时按以下方式判断：

- 如果“保存草稿”成功并进入三阶段详情页，继续第 3 节。
- 如果页面提示粒度、字段、上游或消费场景缺失，但抽屉中没有相应输入框，记录为“创建契约错位”，停止重试。
- 如果 DIMENSION 保存提示 `dimensionProfile` 字段不允许，记录为“维度画像契约错位”，停止重试。

## 3. 模型一：预算执行明细（FACT / DWD）

对应 dbt SQL：[biz_dwd_budget_v2.sql](../pjm/dbt_model/models/dwd/biz_dwd_budget_v2.sql)。

### 3.1 新建模型

| 页面字段 | 填写值 |
|---|---|
| 建设计划 | 选择本次人工测试计划 |
| 业务分类 | 选择当前计划内的“项目管理”分类 |
| 模型类型 | 明细表 |
| 模型名称 | 项目预算执行明细 |
| 用途说明 | 统一保存每条项目预算台账当前快照及预算执行、剩余和超支口径 |

保存后，目标层应由系统显示为 `DWD`。

### 3.2 逻辑设计

| 页面字段 | 填写值 |
|---|---|
| 模型名称 | 项目预算执行明细 |
| 模型说明 | 科研经费三本账当前快照；已执行金额为预付账款、账面成本和应付账款之和 |
| 粒度声明 | 每一行代表一条预算编号对应的预算台账快照 |
| 粒度键 | `budget_id` |
| 事实形态 | 周期快照（PERIODIC_SNAPSHOT） |
| 业务时间 | 快照日期（SNAPSHOT_DATE） |
| 时间字段 | `source_imported_at` |
| 分析维度 | 暂不选择 |
| 相关业务活动 | 项目预算执行管理 |
| 上游逻辑模型 | 留空；本模型直接使用规划物理来源 |

> 原 dbt 模型没有独立业务快照日期，只保留导入时间。本次人工测试用 `source_imported_at` 作为临时快照时间；正式模型应补充明确的 `snapshot_date`。

### 3.3 字段设计

“来源字段”只填写直接可追溯字段；派生字段留空，公式在高级 dbt SQL 中实现。安全等级按现场标准选择；若尚未建立标准，本次草稿可先留空。

| 字段名 | 数据类型 | 字段作用 | 允许为空 | 来源字段 |
|---|---|---|---|---|
| `budget_id` | string | 键 | 否 | 留空，dbt 派生 |
| `source_row_id` | string | 属性 | 否 | `ods_budget_v2.id` |
| `source_table` | string | 属性 | 否 | 留空，dbt 常量 |
| `source_system` | string | 属性 | 否 | `ods_budget_v2._dts_source_system` |
| `source_imported_at` | timestamp | 时间 | 是 | `ods_budget_v2._dts_import_time` |
| `project_no` | string | 属性 | 否 | `ods_budget_v2.project_no` |
| `budget_no` | string | 属性 | 否 | `ods_budget_v2.budget_no` |
| `subtopic` | string | 属性 | 是 | `ods_budget_v2.subtopic` |
| `research_lab` | string | 属性 | 是 | `ods_budget_v2.research_lab` |
| `budget_amount` | decimal | 度量 | 否 | `ods_budget_v2.budget_amount_adjusted` |
| `prepaid_amount` | decimal | 度量 | 否 | `ods_budget_v2.prepaid_amount` |
| `book_cost_amount` | decimal | 度量 | 否 | `ods_budget_v2.book_cost_amount` |
| `payable_amount` | decimal | 度量 | 否 | `ods_budget_v2.payable_amount` |
| `executed_amount` | decimal | 度量 | 否 | 留空，dbt 派生 |
| `remaining_amount` | decimal | 度量 | 否 | 留空，dbt 派生 |
| `overrun_amount` | decimal | 度量 | 否 | 留空，dbt 派生 |
| `is_overrun` | boolean | 属性 | 否 | 留空，dbt 派生 |
| `execution_rate_line` | decimal | 度量 | 是 | 留空，dbt 派生 |
| `etl_time` | timestamp | 时间 | 否 | 留空，dbt 生成 |

应保留的字段质量规则：

- `budget_id`：唯一、非空。
- `budget_no`：唯一、非空。
- `project_no`：非空。

### 3.4 数据实现

| 页面字段 | 选择值 |
|---|---|
| 规范实现输入 | 规划物理资产 |
| 已确认 Landing / 物理来源 | 选择当前计划中的 `public.ods_budget_v2` |
| 目标物化方式 | 表 |
| 去重键 | `budget_id` |
| 系统技术标识 | 保持系统自动生成，不修改 |

只配置直接映射，不把 SQL 公式伪装成字段映射：

| 输入字段 | 目标字段 | 类型转换 |
|---|---|---|
| `id` | `source_row_id` | 字符串 |
| `_dts_source_system` | `source_system` | 保持原类型 |
| `_dts_import_time` | `source_imported_at` | 时间戳 |
| `project_no` | `project_no` | 保持原类型 |
| `budget_no` | `budget_no` | 保持原类型 |
| `subtopic` | `subtopic` | 保持原类型 |
| `research_lab` | `research_lab` | 保持原类型 |
| `budget_amount_adjusted` | `budget_amount` | 小数 |
| `prepaid_amount` | `prepaid_amount` | 小数 |
| `book_cost_amount` | `book_cost_amount` | 小数 |
| `payable_amount` | `payable_amount` | 小数 |

普通字段映射不能表达以下公式，必须由高级 dbt SQL 实现：

```text
budget_id          = 'budget:' + source_row_id
executed_amount    = prepaid_amount + book_cost_amount + payable_amount
remaining_amount   = budget_amount - executed_amount
overrun_amount     = max(0, executed_amount - budget_amount)
is_overrun         = executed_amount > budget_amount
execution_rate_line = executed_amount / budget_amount × 100
```

## 4. 模型二：项目预算执行汇总（SUMMARY / DWS）

对应 dbt SQL：[biz_dws_budget_v2.sql](../pjm/dbt_model/models/dws/biz_dws_budget_v2.sql)。

### 4.1 新建与逻辑设计

| 页面字段 | 填写值 |
|---|---|
| 模型类型 | 汇总表 |
| 模型名称 | 项目预算执行汇总 |
| 用途说明 | 按项目和研究室汇总预算、执行、剩余和超支情况 |
| 输出粒度 | 每一行代表一个项目与研究室组合的当前预算汇总 |
| 粒度键 | `project_no,research_lab` |
| 上游逻辑模型 | 选择“项目预算执行明细”的当前 revision |

目标层应由系统固定为 `DWS`。

字段设计：

| 字段名 | 数据类型 | 字段作用 | 允许为空 |
|---|---|---|---|
| `project_no` | string | 键 | 否 |
| `research_lab` | string | 键 | 是 |
| `item_cnt` | bigint | 度量 | 否 |
| `budget_amount` | decimal | 度量 | 否 |
| `prepaid_amount` | decimal | 度量 | 否 |
| `book_cost_amount` | decimal | 度量 | 否 |
| `payable_amount` | decimal | 度量 | 否 |
| `executed_amount` | decimal | 度量 | 否 |
| `remaining_amount` | decimal | 度量 | 否 |
| `overrun_amount` | decimal | 度量 | 否 |
| `overrun_item_cnt` | bigint | 度量 | 否 |

> 现有 dbt 允许 `research_lab` 为空，却把它作为复合粒度的一部分。这是源模型的数据质量缺口，不要在 UI 中伪造非空；后续应明确补空值归一规则。

### 4.2 数据实现

| 页面字段 | 选择值 |
|---|---|
| 规范实现输入 | 上游逻辑模型 |
| 锁定上游模型版本 | “项目预算执行明细”的当前模型和当前实现版本 |
| 目标物化方式 | 表 |
| 字段映射 | 不为 `COUNT`、`SUM` 添加虚假直接映射 |
| 去重键 | 留空；分组聚合在 dbt SQL 中完成 |

页面会自动保存上游 ModelSpec revision 和 implementation revision。不要再次选择底层 `public.ods_budget_v2`。

## 5. 模型三：预算总量指标应用（APPLICATION / ADS）

对应 dbt SQL：[biz_ads_budget_kpi_v2.sql](../pjm/dbt_model/models/ads/biz_ads_budget_kpi_v2.sql)。

原 dbt 模型是“全局当前快照单行”，没有显式业务键；当前 UI 要求粒度键。为了不把某个金额或数量误当主键，本指南增加一个稳定常量键 `snapshot_scope='ALL'`。这是从旧 dbt 迁入当前契约时必须补的最小设计。

### 5.1 新建与逻辑设计

| 页面字段 | 填写值 |
|---|---|
| 模型类型 | 应用表 |
| 模型名称 | 预算总量指标应用 |
| 用途说明 | 为项目预算看板和指标接口提供当前全局预算总量 |
| 输出粒度 | 每一行代表全平台预算的当前总量快照 |
| 粒度键 | `snapshot_scope` |
| 上游逻辑模型 | 选择“项目预算执行汇总”的当前 revision |
| 消费场景 | 项目预算驾驶舱总览、预算 KPI 接口 |

目标层应由系统固定为 `ADS`。

字段设计：

| 字段名 | 数据类型 | 字段作用 | 允许为空 |
|---|---|---|---|
| `snapshot_scope` | string | 键 | 否 |
| `item_cnt` | bigint | 度量 | 否 |
| `total_budget` | decimal | 度量 | 否 |
| `total_prepaid` | decimal | 度量 | 否 |
| `total_book_cost` | decimal | 度量 | 否 |
| `total_payable` | decimal | 度量 | 否 |
| `total_executed` | decimal | 度量 | 否 |
| `total_remaining` | decimal | 度量 | 否 |
| `overrun_amount` | decimal | 度量 | 否 |
| `overrun_item_cnt` | bigint | 度量 | 否 |

### 5.2 数据实现

| 页面字段 | 选择值 |
|---|---|
| 规范实现输入 | 上游逻辑模型 |
| 锁定上游模型版本 | “项目预算执行汇总”的当前模型和当前实现版本 |
| 目标物化方式 | 表 |
| 字段映射 | 留空；全表聚合和常量键由 dbt SQL 实现 |
| 去重键 | `snapshot_scope` |

进入高级 dbt 工作台后，在原 SQL 的 `SELECT` 首列补：

```sql
'ALL'::text AS snapshot_scope,
```

其余聚合口径保持原 SQL。

## 6. 可选模型四：预算派生指标应用（APPLICATION / ADS）

对应 dbt SQL：[biz_ads_budget_derived_v2.sql](../pjm/dbt_model/models/ads/biz_ads_budget_derived_v2.sql)。

| 页面字段 | 填写值 |
|---|---|
| 模型类型 | 应用表 |
| 模型名称 | 预算派生指标应用 |
| 用途说明 | 提供预算执行率、入账率、应付占比、剩余率、健康度和超支预警 |
| 输出粒度 | 每一行代表全平台预算的当前派生指标快照 |
| 粒度键 | `snapshot_scope` |
| 上游逻辑模型 | 选择“预算总量指标应用”的当前 revision |
| 消费场景 | 项目预算驾驶舱派生指标和超支预警 |
| 数据实现输入 | 上游逻辑模型 |
| 目标物化方式 | 表 |

字段设计：

| 字段名 | 数据类型 | 字段作用 | 允许为空 |
|---|---|---|---|
| `snapshot_scope` | string | 键 | 否 |
| `pjm_budg_execution_rate` | decimal | 度量 | 否 |
| `pjm_budg_book_rate` | decimal | 度量 | 否 |
| `pjm_budg_payable_ratio` | decimal | 度量 | 否 |
| `pjm_budg_remaining_rate` | decimal | 度量 | 否 |
| `pjm_budg_health_score` | decimal | 度量 | 否 |
| `warn_overrun` | boolean | 属性 | 否 |
| `warn_overrun_items` | boolean | 属性 | 否 |

高级 dbt SQL 应从上游透传 `k.snapshot_scope`，其余公式沿用原文件。健康度口径为：

```text
(100 - min(100, 超支金额 / 总预算 × 100)) × 70%
+
(100 - min(100, 应付金额 / 已执行金额 × 100)) × 30%
```

## 7. 独立维度示例：项目节点类型

对应 dbt SQL：[dim_node_type_v2.sql](../pjm/dbt_model/models/dwd/dim_node_type_v2.sql)。

该维度用于说明 DIMENSION 页面，不是预算主链的上游。原模型通过 SQL `VALUES` 生成四条枚举记录。

### 7.1 先在维度目录登记业务维度

| 页面字段 | 填写值 |
|---|---|
| 业务分类 | 项目管理 |
| 维度名称 | 项目节点类型 |
| 业务定义 | 统一描述项目计划中的一般、重要、重大和里程碑节点，供项目进度明细分类和筛选 |
| 责任人 | 当前测试账号或实际维度维护人 |
| 复用范围 | 同一业务分类内复用 |

保存后必须把维度确认成现行版本，再从该维度进入“创建维度表”。

### 7.2 维度表逻辑设计

| 页面字段 | 填写值 |
|---|---|
| 模型名称 | 项目节点类型维度 |
| 维度粒度 | 每一行代表一种标准项目节点类型 |
| 业务键 | `node_type_id` |
| SCD 逻辑策略 | 不保留历史 |
| 复用范围 | 当前业务分类 |
| 分析层级 | 不添加 |

字段设计：

| 字段名 | 数据类型 | 字段作用 | 允许为空 |
|---|---|---|---|
| `node_type_id` | string | 键 | 否 |
| `code` | string | 属性 | 否 |
| `label` | string | 属性 | 否 |
| `is_general` | boolean | 属性 | 否 |
| `is_important` | boolean | 属性 | 否 |
| `is_major` | boolean | 属性 | 否 |
| `is_milestone` | boolean | 属性 | 否 |
| `severity_rank` | integer | 属性 | 否 |

当前普通模式的“受控生成器”仅提供日期维度，不能表达这类内联枚举；也不能把 `DATE_DIMENSION` 错选为节点类型生成器。因此该维度目前只能完成逻辑设计，物化前还需补“静态码表生成器”或完善高级 dbt 绑定。

## 8. 高级 dbt 与普通模式的边界

1. 普通模式创建的是业务可治理的四类 ModelSpec；STG 是系统临时技术节点，不进入四类模型目录。
2. “字段映射”只支持字段名、受控类型转换和有限关联，不能填写 `SUM`、`COUNT`、`CASE`、算术表达式或常量。
3. 保存并校验“数据实现”后，才能从“物理资产”进入高级 dbt 工作台。
4. 若要原样运行现有 dbt 链，`stg_pm__budget_v2` 应作为高级 dbt 项目的技术节点存在，但不再创建一个 STG ModelSpec。
5. 高级 SQL、schema、compile、test、发布证据齐全后，物理资产页才应显示真实产物。

因此，本次手工测试分两步：

- 第一步验证 UI：计划上下文、四类表、粒度、字段角色、上游 revision 和来源版本是否能正确保存。
- 第二步验证物化：进入高级 dbt 后复用现有 SQL，完成编译、测试和真实目标表登记。

## 9. 人工测试记录表

| 检查点 | 期望结果 | 实际结果 |
|---|---|---|
| `public.ods_budget_v2` 已加入来源盘点 | 状态为已确认、当前版本可用 |  |
| 新建 FACT 草稿 | 成功进入三阶段详情页 |  |
| FACT 逻辑设计 | DWD、预算粒度和字段保存成功 |  |
| FACT 数据实现 | 固定 ODS 来源版本，显示系统临时 STG |  |
| 新建 SUMMARY | 可选择 FACT 当前 revision |  |
| SUMMARY 数据实现 | 固定 FACT 当前 implementation |  |
| 新建 APPLICATION | 可选择 SUMMARY，要求填写消费场景 |  |
| 单行应用粒度 | 使用 `snapshot_scope`，不拿金额冒充主键 |  |
| 高级 dbt 入口 | 仅在当前实现校验通过后开放 |  |
| 物理资产 | 只展示真实构建和测试证据 |  |
| DIMENSION 保存 | 若失败，记录是否为 `dimensionProfile` 契约错位 |  |

出现以下情况时停止当前步骤，不要靠重复点击绕过：

- 保存草稿时要求填写抽屉中不存在的粒度、字段或依赖。
- 上游列表为空但上游模型尚未保存当前 revision。
- 来源列表为空或显示旧版本；返回来源盘点修复。
- 应用表只有单行却没有可表达的稳定粒度键。
- 受控生成器没有对应的静态维度类型。
