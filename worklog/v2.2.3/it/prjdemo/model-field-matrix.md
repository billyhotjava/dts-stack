# PRJDEMO 模型页面逐项录入卡

`README.md` 是唯一操作主线。本文件是建模页面的便携录入卡，内容按实际页面从上到下排列，不要求执行者在多个文件之间跳转。

## 1. 先认清页面与两个模型

- 菜单入口：`数据建模 → 维度建模 → 模型工作台`。
- 直接路径：`/data-modeling/dimensions/workbench`。
- 页面标题：`维度建模`。
- 页面区域顺序：`基本信息 → 实现绑定（包含可视化转换）→ 字段管理`。
- 工具栏生命周期：首次为“保存”；保存后依次使用“校验、提交实现、交付检查、发布”，并可查看“日志、质量约束”。

| 创建顺序 | 创建菜单 | 模型名称（填入“基本信息 → 模型名称”） | 物理表名（填入另一个输入框） |
| ---: | --- | --- | --- |
| 1 | 创建明细表 | **项目任务快照明细** | `prjdemo_dwd_project_task_snapshot` |
| 2 | 创建汇总表 | **项目进度汇总** | `prjdemo_dws_project_progress` |

> 模型名称是中文业务名称，物理表名是小写下划线名称。截图中的“字段管理”区域不显示模型名称；需要向上滚动到“基本信息”查看。

由于“时间字段”和“可视化转换”依赖已经建立的字段，实际填写采用两遍：先从上向下填“基本信息”和“实现绑定”的可用项，再填“字段管理”，最后向上返回“实现绑定”补齐时间字段和转换。

## 2. 模型一：项目任务快照明细

模型身份：`项目任务快照明细 / 明细表 / FACT / DWD`。

### 2.1 页面“基本信息”

创建菜单选择“创建明细表”，然后按页面顺序填写：

| 页面字段 | 填写值 |
| --- | --- |
| 数据域 | 项目进度 / `PRJDEMO_PROJECT` |
| 业务过程 | 项目进度快照 / `PRJDEMO_PROJECT_PROGRESS` |
| 模型类型 | 明细表；页面自动给出，后端类型为 `FACT` |
| 数仓分层 | 系统层 `DWD` |
| **模型名称** | **项目任务快照明细** |
| 物理表名 | `prjdemo_dwd_project_task_snapshot`；不要填写 `public.` |
| 业务定义 | 对项目任务在指定快照日期的状态、进度和实际成本进行标准化记录 |
| 模型粒度 | 一个项目任务在一个快照日期一行 |
| 物化方式 | 表 / `table` |
| 加载策略 | 全量 / `FULL` |
| 分区字段 | 首次最小 Demo 留空 |

核对结果：模型名称为“项目任务快照明细”；最终物理表为 `public.prjdemo_dwd_project_task_snapshot`。这两个值不是同一个字段。

### 2.2 页面“实现绑定”第一遍

按页面顺序设置：

| 页面控件 | 操作或填写值 |
| --- | --- |
| 实现输入方式 | 关联数仓来源 |
| 物理来源 → 维护来源 | 选择 ODS 数据集和 `ods_prjdemo_project_task_snapshot`，点击“登记并确认” |
| 物理来源 | 来源显示 `CONFIRMED · CURRENT` 后关闭弹窗，再勾选该来源 |
| 引用维度模型 | 本最小 Demo 不选择 |
| 事实类型 | 周期快照 / `PERIODIC_SNAPSHOT` |
| 时间语义 | 快照日期 / `SNAPSHOT_DATE` |
| 时间字段 | 第一遍暂不选择；页面会提示先在字段管理新增“时间”字段 |
| 可视化转换 | 第一遍暂不配置；目标字段尚未建立 |

规划来源和当前模型输入是两个动作：“登记并确认”把 ODS 加入当前规划；关闭弹窗后在“物理来源”勾选，才把它绑定为本模型输入。

### 2.3 页面“字段管理”

页面可见控件和本次用法：

1. “从表/视图导入”当前不可用，提示“当前版本尚无字段级表结构导入契约”。
2. “插入行数”填写 `12`，点击“插入字段”。如果已经有 1 行，则只补到总计 12 行，不要再插入 12 行。
3. 基础列按页面显示为“字段名称、类型、字段显示名、字段作用、允许为空”。
4. 点击“字段显示设置”后，再填写“字段标准、字段密级”；可用“批量字段密级”统一选择“公开”。
5. 本 Demo 全部字段不允许为空，“允许为空”全部不勾选。

| 序号 | 字段名称 | 类型 | 字段显示名 | 字段作用 | 允许为空 | 字段标准 | 字段密级 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | `task_snapshot_id` | STRING | 任务快照标识 | 键（KEY） | 否 | `PRJDEMO_TASK_SNAPSHOT_ID` | 公开 |
| 2 | `project_snapshot_id` | STRING | 项目快照标识 | 属性 | 否 | `PRJDEMO_PROJECT_SNAPSHOT_ID` | 公开 |
| 3 | `snapshot_date` | DATE | 快照日期 | 时间 | 否 | 不绑定 | 公开 |
| 4 | `project_code` | STRING | 项目编码 | 属性 | 否 | 不绑定 | 公开 |
| 5 | `project_name` | STRING | 项目名称 | 属性 | 否 | 不绑定 | 公开 |
| 6 | `task_code` | STRING | 任务编码 | 属性 | 否 | 不绑定 | 公开 |
| 7 | `task_name` | STRING | 任务名称 | 属性 | 否 | 不绑定 | 公开 |
| 8 | `task_status_code` | STRING | 任务状态编码 | 属性 | 否 | 不绑定；值域按 `PRJDEMO_TASK_STATUS` 验收 | 公开 |
| 9 | `progress_pct` | DECIMAL | 完成进度 | 度量 | 否 | `PRJDEMO_PROGRESS_PCT` | 公开 |
| 10 | `plan_end_date` | DATE | 计划结束日期 | 属性 | 否 | 不绑定 | 公开 |
| 11 | `actual_cost` | DECIMAL | 实际成本 | 度量 | 否 | `PRJDEMO_ACTUAL_COST` | 公开 |
| 12 | `source_batch_id` | STRING | 来源批次标识 | 属性 | 否 | 不绑定 | 公开 |

截图中已经录入的 `project_snapshot_id` 应补成：类型 `STRING`、字段显示名“项目快照标识”、字段作用“属性”、“允许为空”不勾选。最终只需保证上述 12 个字段各一行且无重复；建议按表中顺序排列。

### 2.4 返回“实现绑定”第二遍

先在“时间字段”勾选 `snapshot_date`，再在“可视化转换”点击“按名称一一映射”并逐行核对：

| 目标字段 | 来源字段 | 类型转换 | 去重键 | 分组字段 | 聚合 | 聚合来源 |
| --- | --- | --- | --- | --- | --- | --- |
| `task_snapshot_id` | `task_snapshot_id` | 不转换 | 勾选 | 不勾选 | 不聚合 | 留空 |
| `project_snapshot_id` | `project_snapshot_id` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `snapshot_date` | `snapshot_date` | 源已为 DATE 时不转换，否则选择“日期” | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `project_code` | `project_code` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `project_name` | `project_name` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `task_code` | `task_code` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `task_name` | `task_name` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `task_status_code` | `task_status_code` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `progress_pct` | `progress_pct` | 源已为数值时不转换，否则选择“小数” | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `plan_end_date` | `plan_end_date` | 源已为 DATE 时不转换，否则选择“日期” | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `actual_cost` | `actual_cost` | 源已为数值时不转换，否则选择“小数” | 不勾选 | 不勾选 | 不聚合 | 留空 |
| `source_batch_id` | `source_batch_id` | 不转换 | 不勾选 | 不勾选 | 不聚合 | 留空 |

DWD 不配置关联、过滤、分组或聚合，只有 `task_snapshot_id` 勾选“去重键”。

### 2.5 页面工具栏

依次执行并分别记录证据：`保存 → 校验 → 提交实现 → 交付检查 → 发布`。然后在“日志”及物理资产中记录运行 ID，并确认 `public.prjdemo_dwd_project_task_snapshot` 为 12 列、8 行、8 个唯一 `task_snapshot_id`。

## 3. 模型二：项目进度汇总

模型身份：`项目进度汇总 / 汇总表 / SUMMARY / DWS`。

### 3.1 页面“基本信息”

返回模型列表，选择“新建模型 → 创建汇总表”，然后按页面顺序填写：

| 页面字段 | 填写值 |
| --- | --- |
| 数据域 | 项目进度 / `PRJDEMO_PROJECT` |
| 模型类型 | 汇总表；页面自动给出，后端类型为 `SUMMARY` |
| 数仓分层 | 系统层 `DWS` |
| **模型名称** | **项目进度汇总** |
| 物理表名 | `prjdemo_dws_project_progress`；不要填写 `public.` |
| 业务定义 | 按项目和快照日期汇总任务数量、平均进度和实际成本 |
| 模型粒度 | 一个项目在一个快照日期一行 |
| 物化方式 | 表 / `table` |
| 加载策略 | 全量 / `FULL` |
| 分区字段 | 首次最小 Demo 留空 |

核对结果：模型名称为“项目进度汇总”；最终物理表为 `public.prjdemo_dws_project_progress`。

### 3.2 页面“实现绑定”第一遍

| 页面控件 | 操作或填写值 |
| --- | --- |
| 实现输入方式 | 关联上游模型 |
| 上游模型 | 勾选已提交实现并发布的“项目任务快照明细”当前修订 |
| 可视化转换 | 第一遍暂不配置；目标字段尚未建立 |

本 Demo 不为 DWS 直接选择 ODS，也不配置维度引用或 JOIN。

### 3.3 页面“字段管理”

“插入行数”填写 `7` 后点击“插入字段”。全部“允许为空”不勾选；点击“字段显示设置”后绑定标准，并将字段密级统一设为“公开”。

| 序号 | 字段名称 | 类型 | 字段显示名 | 字段作用 | 允许为空 | 字段标准 | 字段密级 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | `project_snapshot_id` | STRING | 项目快照标识 | 键（KEY） | 否 | `PRJDEMO_PROJECT_SNAPSHOT_ID` | 公开 |
| 2 | `snapshot_date` | DATE | 快照日期 | 时间 | 否 | 不绑定 | 公开 |
| 3 | `project_code` | STRING | 项目编码 | 属性 | 否 | 不绑定 | 公开 |
| 4 | `project_name` | STRING | 项目名称 | 属性 | 否 | 不绑定 | 公开 |
| 5 | `task_total` | BIGINT | 任务总数 | 度量 | 否 | `PRJDEMO_TASK_COUNT` | 公开 |
| 6 | `avg_progress_pct` | DECIMAL | 平均完成进度 | 度量 | 否 | `PRJDEMO_PROGRESS_PCT` | 公开 |
| 7 | `actual_cost_amount` | DECIMAL | 实际成本金额 | 度量 | 否 | `PRJDEMO_ACTUAL_COST` | 公开 |

### 3.4 返回“实现绑定 → 可视化转换”第二遍

按下表配置。三个度量字段与上游字段不同，不能保留错误的同名自动映射。

| 目标字段 | 来源字段 | 类型转换 | 去重键 | 分组字段 | 聚合 | 聚合来源 |
| --- | --- | --- | --- | --- | --- | --- |
| `project_snapshot_id` | `project_snapshot_id` | 不转换 | 不勾选 | 勾选 | 不聚合 | 留空 |
| `snapshot_date` | `snapshot_date` | 不转换 | 不勾选 | 勾选 | 不聚合 | 留空 |
| `project_code` | `project_code` | 不转换 | 不勾选 | 勾选 | 不聚合 | 留空 |
| `project_name` | `project_name` | 不转换 | 不勾选 | 勾选 | 不聚合 | 留空 |
| `task_total` | `task_code` | 不转换 | 不勾选 | 不勾选 | 计数（COUNT） | `task_code` |
| `avg_progress_pct` | `progress_pct` | 不转换 | 不勾选 | 不勾选 | 平均值（AVG） | `progress_pct` |
| `actual_cost_amount` | `actual_cost` | 不转换 | 不勾选 | 不勾选 | 求和（SUM） | `actual_cost` |

DWS 不配置关联、过滤或去重。4 个非度量输出勾选“分组字段”，3 个度量输出配置聚合函数和聚合来源。

### 3.5 页面工具栏和预期结果

依次执行并分别记录证据：`保存 → 校验 → 提交实现 → 交付检查 → 发布`。最终确认 `public.prjdemo_dws_project_progress` 为 2 行：

| project_snapshot_id | snapshot_date | project_code | project_name | task_total | avg_progress_pct | actual_cost_amount |
| --- | --- | --- | --- | ---: | ---: | ---: |
| PPS_PRJA_20260901 | 2026-09-01 | PRJ-A | 数据治理平台升级 | 4 | 72.50 | 91000.00 |
| PPS_PRJB_20260901 | 2026-09-01 | PRJ-B | 经营分析看板建设 | 4 | 53.75 | 51000.00 |

模型物化后以物理表结果为准，并在 `evidence-register.md` 记录模型名称、ModelSpec ID、修订、运行 ID 和查询证据。
