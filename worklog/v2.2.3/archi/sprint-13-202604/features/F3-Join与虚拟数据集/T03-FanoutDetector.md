# T03: FanoutDetector + SymmetricAggregate

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

**这是整个语义层最容易出错的地方，也是自助 BI 的信任基石。** 实现：

1. **FanoutDetector** — 识别查询是否涉及 1:N 扇出 join
2. **SymmetricAggregate** — 对 fanout 场景自动改写 SQL：每个 measure 先在其原生 model 粒度聚合后再 join，避免重复计数

参考：Looker 的 `symmetric_aggregates`、Cube 的 symmetric aggregates、Lightdash 的 fanout handling。这个算法在三家文档里都有开源描述。

## 问题示例

### 天真的错误 SQL

场景：orders 和 payments 是 1:N（一个订单可能有多笔支付）。
用户请求：`count(orders.order_id) + sum(payments.amount)` by `customer.region`。

**错的**：
```sql
SELECT c.region, COUNT(DISTINCT o.order_id), SUM(p.amount)
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.customer_id
LEFT JOIN payments p ON o.order_id = p.order_id
GROUP BY c.region
```
问题：`COUNT(DISTINCT order_id)` 还行但效率差；更要命的是若用 `COUNT(o.order_id)` 会被 payments 放大 3 倍。`SUM(p.amount)` 没问题（本身就是 payments 层）。但若加一个 `AVG(o.amount)` 就被放大了。

### 对称聚合改写

```sql
WITH orders_agg AS (
  SELECT customer_id, COUNT(order_id) AS order_count, AVG(amount) AS avg_amount
  FROM orders
  GROUP BY customer_id
),
payments_agg AS (
  SELECT o.customer_id, SUM(p.amount) AS total_payment
  FROM payments p
  JOIN orders o ON p.order_id = o.order_id
  GROUP BY o.customer_id
)
SELECT c.region,
       SUM(oa.order_count)   AS order_count,
       AVG(oa.avg_amount)    AS avg_amount,  -- 注意：这里的 AVG 逻辑要谨慎
       SUM(pa.total_payment) AS total_payment
FROM customers c
LEFT JOIN orders_agg oa ON c.customer_id = oa.customer_id
LEFT JOIN payments_agg pa ON c.customer_id = pa.customer_id
GROUP BY c.region
```

**核心原则：** 每个 measure 在其**原生 model 的自然粒度**上预聚合，然后再 join，这样扇出不会放大任何 measure。

## 技术设计

### 1. FanoutDetector 算法

输入：`QueryRequest` + `JoinGraph`。

```
对每个请求的 join：
  如果 join.type ∈ {ONE_TO_MANY, MANY_TO_MANY}：
    标记该 join 为 fanout
  若有任意 fanout join：
    返回 FanoutAnalysis { hasFanout=true, fanoutJoins=[...], measuresByModel=... }
```

### 2. measure 归属识别

对 request 里每个 measure，确定其原生 model（从 meta 拿 `source_model`）。

```java
public record MeasureAttribution(
    String measureId,
    String nativeModel,
    String aggregation,       // sum / count / count_distinct / custom_sql
    String sqlExpression      // SUM(revenue_cents) 或 custom_sql 里的片段
) {}
```

**特例**：`count_distinct` 本身在扇出后仍然对（因为 distinct），但为保持一致性也走预聚合路径，性能差一点但正确性稳。

`custom_sql` 型 measure：在预聚合 CTE 里直接展开用户写的 SQL 片段，再对结果做外层 SUM。

### 3. SymmetricAggregate 改写策略

```
1. 识别所有参与 measure 的 model
2. 为每个 model 生成一个 CTE：
     SELECT <该 model 的 grain 维度>, <该 model 的 measures 聚合>
     FROM <model>
     [JOIN 必须的上游表以获取 grain 键]
     GROUP BY <grain>
3. 外层查询 JOIN 所有 CTE，按用户请求的 dimension GROUP BY
4. 外层聚合根据原聚合语义选择函数：
     inner SUM   → outer SUM
     inner COUNT → outer SUM
     inner AVG   → outer AVG of weighted avg (需 COUNT 辅助列，复杂)
     inner MIN/MAX → outer MIN/MAX
     inner count_distinct → outer SUM (因为内层已 distinct)
```

**AVG 的坑**：简单外层 AVG 会丢权重。本 Sprint **拒绝 fanout + AVG** 组合，返回 422 + 明确错误："fanout join with AVG is not supported; use SUM/COUNT or separate card"。后续 Sprint 再支持 weighted AVG。

### 4. dimension 归属

用户选的 dimension 必须能从某张 CTE 拿到。如果 dimension 属于"fanout 的另一侧"，要判断：
- 这个 dimension 是用于 **切分**（GROUP BY）→ 对所有 CTE 都 join 它，CTE 里带上该维度
- 这个 dimension 是用于 **筛选**（WHERE）→ 推到每个 CTE 的 WHERE 里，避免外层 JOIN 完再 filter

### 5. 警告机制

即使 SymmetricAggregate 改写成功，也在 `meta.warnings` 里加：
```json
{ "code": "W_FANOUT_REWRITTEN", "message": "Query involves 1:N join, rewritten with symmetric aggregates. SQL preview shows the rewritten form." }
```

让用户知道 SQL 比预期复杂的原因。

### 6. 拒绝清单

即使本 Task 实现，以下组合暂拒绝（返回 422）：
- fanout + AVG measure
- fanout + custom_sql measure（复杂，Sprint-14 再支持）
- 超过 2 个 fanout join 连在一起（组合太复杂）
- fanout + 派生指标（F4 交集，先让 F4 独立正确）

### 7. 测试矩阵

极其重要，必须每条都验证 SQL 正确且结果正确：

| 场景 | 验证 |
|---|---|
| orders × payments, COUNT(orders) | 预聚合 + 外层 SUM |
| orders × payments, SUM(payments) | 预聚合 + 外层 SUM |
| orders × payments × customer, GROUP BY region | 多 CTE + 外层 join customer |
| 纯 M:1（customer × region）不改写 | 走 T02 直通路径 |
| fanout + AVG | 422 |
| fanout + count_distinct | 允许，预聚合里用 DISTINCT，外层 SUM |
| fanout + MIN/MAX | 外层 MIN/MAX |

### 8. 参考资料

- Looker "Symmetric Aggregates" 文档（搜索引擎可查）
- Cube 源码 `packages/cubejs-schema-compiler/src/adapter/PreAggregations.js` 里的 fanout 处理
- Lightdash docs "symmetric aggregates"

阅读这三份材料有助于对齐算法。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/compiler/FanoutDetector.java` |
| 新建 | `service/semantic/compiler/SymmetricAggregateRewriter.java` |
| 修改 | `MultiModelCompiler.compile()` 分流到 rewriter |
| 测试 | 重点 IT：每条 SQL 手工写参考版本 + 实际版本对比数据（`it/evidence/f3-fanout-matrix.md`） |

## 验证

- [ ] 6 个 fanout 场景的 SQL 对比存 `it/evidence/f3-fanout-matrix.md`
- [ ] 每个场景都有"对比结果" — 手工写的对照 SQL 跑出的数字 vs 自动改写的数字一致
- [ ] 拒绝清单里的组合全部返回 422
- [ ] 非 fanout 查询**不进入**此路径（性能敏感）
- [ ] 警告机制生效

## 完成标准

- [ ] Fanout 场景 SQL 改写正确率 100%（以参考 SQL 为准）
- [ ] 单元 + 集成测试覆盖率 ≥ 90%（这块错不得）
- [ ] 文档 `assets/specs/06-symmetric-aggregate-algorithm.md` 描述算法细节
- [ ] 所有 rewriter 改写过的 SQL 都能在 PG/Doris 执行不报错
