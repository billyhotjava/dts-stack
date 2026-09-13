# Sprint-65 架构与模块边界

## 1. 逻辑架构

```text
                            ┌────────────────────┐
BUSINESS_FIRST ────────────>│                    │
                            │   WarehousePlan    │
ASSET_FIRST ───────────────>│ + PlanningBaseline│
                            └─────────┬──────────┘
                                      │ planId
                    ┌─────────────────┼─────────────────┐
                    v                 v                 v
             数据建设工作台      经典模型中心       高级 dbt
              证据与导航       ModelSpec 真值     实现产物与运行
                    │                 │                 │
      ┌─────────────┼─────────────┐   └────────┬────────┘
      v             v             v            v
 连接/接入       标准管理       资产/指标       血缘/质量/调度
 专业所有者      专业所有者     专业所有者       专业所有者
```

## 2. 后端模块落点

| 能力 | 首选模块 | 复用事实 | 新增责任 |
|---|---|---|---|
| WarehousePlan 聚合 | `source/dts-platform` modeling | `modeling_warehouse_plan` | 方案级聚合、绑定、版本、评审 |
| ModelSpec | `source/dts-platform` modeling vNext | model spec/revision/dependency | 补足事实形态、时间语义和规划引用 |
| 标准绑定 | `source/dts-platform` metadata standards | standard binding/revision | 计划级证据与漂移摘要 |
| dbt | modeling vNext + dbt runner | artifact/compile/run/lineage | 所有权冲突和回写完整性 |
| 工作台投影 | `source/dts-platform` application/query | 跨模块真实记录 | stage projection 与 next action |
| 前端 | `source/dts-platform-webapp` | 现有规划/模型/dbt 页面 | 新 shell、上下文、菜单兼容 |

## 3. 复用与替换

### 3.1 直接复用

- `ModelingVNextContract` 中的 Layer、ModelType、Grain、SourceRef、StandardBinding、ModelSpec；
- `/api/modeling/vnext` 的 model specs、dependencies、dbt import、artifact、drift、release gate、compile、runs、lineage；
- metadata standards API；
- `modeling_model_spec`、revision、standard binding、dbt artifact、pipeline run 和 lineage edge 表。

### 3.2 扩展

- `modeling_warehouse_plan` 从过程/分层登记扩展为方案级聚合；
- 粒度补充 `factShape` 和 `timeSemantics`；
- 工作台补充计划级证据投影；
- dbt 补充实现所有权与模型台账回写状态。

### 3.3 受控替换

- 旧 `modeling_plan*` 写路径；
- sessionStorage 规划上下文事实源；
- 建模工作台到主题域管理的重定向；
- 把 DBT_NATIVE 当建模模式的 UI；
- 从页面序号推导完成状态的旅程逻辑。

## 4. 跨模块引用规则

- 引用必须保存目标 ID、目标类型、目标版本（若支持）和最近校验时间；
- 投影读取失败返回 `UNKNOWN` 或 `STALE`，不能伪装为零条或已完成；
- 规划模块不能直接更新连接、标准、资产或指标正文；
- 目标被删除、归档或无权访问时，规划保留引用并显示诊断状态；
- 发布门禁只消费确认过的引用和当前有效版本。

## 5. 通用性检查

平台核心仅允许以下中性分类：

- 起点：BUSINESS_FIRST / ASSET_FIRST；
- 模型角色：FACT / DIMENSION / SUMMARY / APPLICATION；
- 事实形态：TRANSACTION / PERIODIC_SNAPSHOT / ACCUMULATING_SNAPSHOT；
- 对象性质：MASTER / MANAGEMENT / REFERENCE；
- 时间语义：EVENT_TIME / SNAPSHOT_DATE / PERIOD / MILESTONE_DATES；
- 实现方式：DESIGNER_GENERATED / DBT_MANAGED。

行业模板只能为这些中性概念提供默认值、示例和候选，不允许增加核心状态机分支。
