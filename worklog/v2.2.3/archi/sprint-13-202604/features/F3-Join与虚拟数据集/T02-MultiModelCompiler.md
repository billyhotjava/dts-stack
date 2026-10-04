# T02: MultiModelCompiler — 多表 join 编译

**优先级**: P0
**状态**: READY
**依赖**: T01, F2/T03

## 目标

扩展 F2 的 SingleModelCompiler，支持沿着 JoinGraphRegistry 声明过的 edge 做多表 join 编译。本 Task 只处理"**非 fanout**"场景（1:1 和 N:1）；1:N 的 fanout 场景在 T03 的 SymmetricAggregate 专门处理。

## 技术设计

### 1. 请求形态

```json
{
  "base": "ads_sales_daily",
  "joins": [
    { "to": "dim_customer", "via": "ads_sales_daily.customer_id" }
  ],
  "measures": ["ads_sales_daily.revenue"],
  "dimensions": ["dim_customer.region"],
  "filters": [...]
}
```

### 2. 编译流程

```
QueryRequest
    ↓
resolveJoinPaths()               → 用 JoinGraphRegistry 查每个 join
    ↓
validateJoinWhitelist()          → 任何一条不在白名单 → 422
    ↓
detectFanout()                   → T03 处理；若命中 fanout，切换到 SymmetricAggregate 路径
    ↓
[非 fanout 路径]
buildFromClause()                → ads_sales_daily AS base INNER JOIN dim_customer AS t1 ON base.customer_id = t1.customer_id
    ↓
... 后续同 SingleModelCompiler
```

### 3. FROM/JOIN 生成

- `base` 永远作为 primary table，alias `base`
- 每个 join 分配 alias `t1`, `t2`, ...
- JoinType → SQL JOIN 关键字映射：
  - `many_to_one` → `LEFT JOIN`（默认）或 `INNER JOIN`（若 meta.relationship=inner）
  - `one_to_one` → `LEFT JOIN`
  - `one_to_many` / `many_to_many` → 走 T03 SymmetricAggregate 路径（不在本 Task 输出）
- `on_clause` 模板渲染：`{{this}}` → base alias，`{{to}}` → target alias

### 4. 字段解析

`"dim_customer.region"` → `t1.region`（alias 映射）
`"ads_sales_daily.revenue"` → `SUM(base.revenue_cents)`（从 metric 定义读）

### 5. 字段冲突

- 两个 model 都有 `region` 列 → select 时必须带 alias 限定（`t1.region` 而不是 `region`）
- 所有 select 表达式 AS 用户可读的 id：`t1.region AS "dim_customer__region"`
- 前端从元信息里已经用全限定 id 请求，无歧义

### 6. 白名单校验

对每个请求的 join：
```java
Optional<JoinEdge> edge = joinGraphRegistry.directEdge(base, join.to());
if (edge.isEmpty()) {
    throw new SemanticException(422, "JOIN_NOT_WHITELISTED", ...);
}
if (edge.get().approvalRequired() && !user.hasApproval(edge.get())) {
    throw new SemanticException(422, "JOIN_REQUIRES_APPROVAL", ...);
}
```

### 7. 密级传导

- `effective_security_level = max(base.level, all joined models.level)`
- 传给 SecurityInjector 做用户校验
- 若结果 level 超用户上限 → 编译失败 422

### 8. 非 fanout 测试矩阵

| 场景 | 样例 |
|---|---|
| N:1 单 join | orders × customer，正确带 LEFT JOIN |
| N:1 多 join | orders × customer × product，两次 JOIN |
| 字段别名冲突 | 两 model 都有 region，各自按 alias 限定 |
| 非白名单 join | 422 |
| approval_required 但用户无权限 | 422 |
| 高密级 join 目标 | 用户密级不够 → 422 |

### 9. 1:N fanout 交接给 T03

当 `detectFanout()` 返回 true：
- **本 Task 不处理**，抛 `FanoutDetectedException`，由 T03 的 SymmetricAggregate 接管
- 本 Task 的工作是正确识别并抛异常，让 T03 有明确切换点

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/compiler/MultiModelCompiler.java` |
| 新建 | `service/semantic/compiler/JoinResolver.java` |
| 修改 | `SemanticQueryResource` 根据 request 里 joins 是否为空分流到 Single/Multi |
| 测试 | `MultiModelCompilerTest`（15+ 用例），IT |

## 验证

- [ ] 10 条端到端 SQL 写入 `it/evidence/f3-sql-review.md`，对比 DB 结果
- [ ] 非白名单 join 返回 422 + 明确错误码
- [ ] alias 冲突自动处理
- [ ] 密级传导正确
- [ ] SQL 注入测试通过
- [ ] 性能：3 表 join 编译时间 < 10ms

## 完成标准

- [ ] 两表 join 端到端从 meta → request → SQL → 执行结果 全跑通
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 与 T03 交接契约明确（FanoutDetectedException 接口）
