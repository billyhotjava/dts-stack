# T23: Query Plan（后端 EXPLAIN 封装 + 前端 React Flow 渲染）

**优先级**: P1
**状态**: READY
**依赖**: F1

## 目标

补全现有 `query_execution.planDigest` 字段的实际实现。支持 Trino / Hive / PostgreSQL 三引擎的 EXPLAIN，统一为 PlanNode 树返回，前端树形可视化。

## 技术设计

### 后端 SqlPlanService

```java
public interface SqlPlanService {
    PlanResult explain(String sql, Engine engine, UUID datasourceId);
}

public record PlanResult(
    PlanNode root,
    String rawText,          // 原始 EXPLAIN 输出
    Engine engine,
    Duration explainTime
) {}

public record PlanNode(
    String id,
    String operator,         // TableScan / HashJoin / Aggregate ...
    String table,
    Double estimatedRows,
    Double estimatedCost,
    Map<String, String> attributes,
    List<PlanNode> children
) {}
```

### 引擎实现

| 引擎 | 语句 | 解析方式 |
|---|---|---|
| **Trino** | `EXPLAIN (FORMAT JSON) <sql>` | Jackson 解析 JSON 树 |
| **Hive** | `EXPLAIN <sql>` | 按缩进逐行文本解析 |
| **PostgreSQL** | `EXPLAIN (FORMAT JSON, ANALYZE false) <sql>` | Jackson JSON 树 |

### 新端点

```
POST /api/sql/v2/explain
Body: { sql, engine, datasourceId }
Response: PlanResult
```

- 审计 `SQL_PLAN_VIEW`
- EXPLAIN 的 SQL 也要过 `SecuritySqlRewriter`（安全策略一致）

### 前端渲染

- 复用项目已有 `react-flow` 依赖（大屏编辑器已用）
- 第一版：**树形纵向展示**（不做 DAG）
- 节点卡片显示：operator、table、estimatedRows、cost bar
- 代价深浅：估算行数大的节点用深红色背景
- 点击节点：右侧面板显示完整 attributes
- 顶部 Tab 切换：Tree View / Raw Text View

### 性能

- 大 SQL EXPLAIN 可能几百个节点，React Flow 渲染需虚拟化（React Flow 原生支持）

## 影响范围

- 新增 `SqlPlanService` + 三个 engine adapter
- `SqlIdeResource` 新增 `POST /api/sql/v2/explain`
- 新增 `result/QueryPlanView.tsx`

## 验证

- [ ] Trino / Hive / PostgreSQL 三种引擎 EXPLAIN 都可用
- [ ] 树形展示正确，父子关系对齐实际计划
- [ ] Raw Text Tab 显示原始输出（开发者友好）
- [ ] 审计正确

## 完成标准

- [ ] 后端 3 引擎实现完整
- [ ] 前端树形渲染可用
- [ ] DAG 视图（v2.1 再做）留为后续任务
