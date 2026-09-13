# T03: SingleModelCompiler — 单表 group-by 编译

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现查询编译器的第一阶段：**单张 model 上的 `measures × dimensions × filters × time_granularity → SQL`**。不处理 join、不处理派生指标。这是整个语义层的最核心引擎，写对了一切都有基础。

## 技术设计

### 1. 输入契约（QueryRequest，子集）

```java
public record QueryRequest(
    String base,
    List<MeasureRef> measures,           // 单元素即可
    List<DimensionSelector> dimensions,
    List<FilterClause> filters,
    List<OrderClause> orderBy,
    Integer limit,
    String cacheHint                     // "fresh" | null
) {}

public record DimensionSelector(
    String id,
    @Nullable String granularity        // 仅 time 类 dimension
) {}

public record FilterClause(
    String field,
    String op,                          // = != > >= < <= in not_in between like is_null is_not_null
    Object value                        // 标量 / 数组 / [low, high]
) {}
```

### 2. 编译流程

```
QueryRequest
    ↓
validate()                       → 字段存在、类型匹配、op 合法
    ↓
resolveMetadata()                → 从 meta 拿 aggregation_type / column_name
    ↓
SqlBuilder (StringBuilder + 参数列表)
    ↓
applyTimeGranularity()           → date_trunc(month, order_date) AS order_date_month
    ↓
applyDimensionSelects()
    ↓
applyMeasureSelects()            → 根据 aggregation_type 生成 SUM(x) / COUNT(DISTINCT x) 等
    ↓
applyFilters()                   → 参数化 WHERE
    ↓
applyGroupBy()                   → GROUP BY 所有 dimension expression（按位置 OR 列别名看方言）
    ↓
applyOrderLimit()
    ↓
SecurityInjector.inject()        → T04 处理
    ↓
CompiledQuery { sql, params, dialect }
```

### 3. 方言抽象

```java
public interface DialectAdapter {
    String truncDate(String expr, String granularity);      // Postgres vs Doris 不同
    String quoteIdent(String ident);
    String formatLiteral(Object value, ColumnType type);
    String countDistinct(String expr);
    boolean supportsLateral();
    // ...
}
```

本 Sprint 实现：
- `PostgresDialectAdapter`
- `DorisDialectAdapter`（如果客户现场用 Doris）

### 4. 时间粒度处理

```
granularity      → Postgres              → Doris
----------       ----------               ----------
hour             date_trunc('hour', x)    date_trunc('hour', x)
day              date_trunc('day', x)     date_trunc('day', x)
week             date_trunc('week', x)    date_trunc('week', x)
month            date_trunc('month', x)   date_trunc('month', x)
quarter          date_trunc('quarter', x) date_trunc('quarter', x)
year             date_trunc('year', x)    date_trunc('year', x)
```

**约束：** granularity 必须是 dimension 声明过的（`meta.dts.dimension.granularities`），否则 400。

### 5. Filter op 编译表

| op | SQL |
|---|---|
| `=` | `field = ?` |
| `!=` | `field <> ?` |
| `>` `>=` `<` `<=` | `field > ?` etc. |
| `in` | `field IN (?, ?, ...)` |
| `not_in` | `field NOT IN (...)` |
| `between` | `field BETWEEN ? AND ?` |
| `like` | `field LIKE ?`（客户端传 `%x%`） |
| `is_null` | `field IS NULL` |
| `is_not_null` | `field IS NOT NULL` |

**完全禁止**任何未在表中的 op。任何类似 `op=custom` 的请求直接 400。

### 6. 参数化

所有字面量走 PreparedStatement 参数，不拼接到 SQL。防 SQL 注入。

例外：`field` 自身用 `DialectAdapter.quoteIdent` 处理，但 field name 必须是白名单字段（从元信息匹配）。

### 7. `sql_preview` 的生成

前端 SQL 预览需要**可读**的 SQL。做法：
- 编译时保留 placeholder `?`
- 单独走一条 `formatForPreview()`，把参数**渲染回 SQL 字面量**，同时对字符串加引号、转义
- **真正执行的是参数化版本**，preview 仅为展示

### 8. 测试矩阵

| 场景 | 样例 |
|---|---|
| 无 dimension | `SELECT SUM(revenue) FROM ads_sales_daily` |
| 单类别 dimension | `SELECT region, SUM(revenue) FROM ... GROUP BY region` |
| 时间 granularity=month | `SELECT date_trunc('month', order_date), SUM(revenue) ... GROUP BY 1` |
| 多 filter 含 in | `WHERE region IN (?, ?) AND order_date >= ?` |
| count_distinct | `COUNT(DISTINCT order_id)` |
| limit + order | `ORDER BY SUM(revenue) DESC LIMIT 100` |
| 字段不存在 | 400 + 错误码 |
| op 不合法 | 400 + 错误码 |
| Postgres vs Doris 方言 | 日期函数切换 |

### 9. 非目标（本 Task 不做）

- 多表 join（F3）
- 派生指标（F4）
- 窗口函数
- fanout symmetric aggregate（F3-T03）
- Arrow 返回（F2-T05 做 JSON，Arrow 留 flag）

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/compiler/SingleModelCompiler.java` |
| 新建 | `service/semantic/compiler/DialectAdapter.java` + 2 实现 |
| 新建 | `service/semantic/compiler/QueryRequest.java` + records |
| 新建 | `web/rest/semantic/SemanticQueryResource.java` |
| 新建 | `service/semantic/compiler/SqlBuilder.java` |
| 测试 | `SingleModelCompilerTest`（30+ 用例）、`SemanticQueryResourceIT` |

## 验证

- [ ] 生成的 SQL 通过人工 review 正确（至少 10 条 SQL 写入 `it/evidence/f2-sql-review.md` 并对比 DB 执行结果）
- [ ] Postgres + Doris 两套方言都能跑
- [ ] SQL 注入测试：filter value 含 `'; DROP TABLE ...` 不影响数据库
- [ ] 非法 field 400、非法 op 400、非法 granularity 400
- [ ] 性能：100 万行单表 group-by p95 < 3s（DB 侧的事，编译器自身 < 5ms）
- [ ] 单元测试覆盖率 ≥ 85%（编译器这种纯逻辑应该高覆盖）

## 完成标准

- [ ] `/api/semantic/query` 对单表请求返回正确结果
- [ ] `POST /api/semantic/query/preview-sql` 返回人类可读 SQL
- [ ] 3 个端到端样例（销售 / 订单 / 客户）从 meta → query → 结果全跑通
- [ ] 样例 SQL + 执行结果对比表存 `it/evidence/f2-e2e-single-model.md`
