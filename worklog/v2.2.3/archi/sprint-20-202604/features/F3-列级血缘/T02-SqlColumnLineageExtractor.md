# T02: SqlColumnLineageExtractor 实现

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

实现一个能从 dbt 编译后的 ANSI SQL 提取列级血缘的解析器：输入 `compiled_sql` + 已知上游表的列名清单，输出列对列的映射关系。

## 技术设计

### 实现选型

| 选项 | 评价 |
|---|---|
| 自己用正则 | 列级解析逃不过 AST，正则极易出错 |
| **JSqlParser** | Java 生态成熟，支持 SELECT/CTE/JOIN/CASE，主流 SQL 方言够用 ✅ |
| Calcite | 太重，引入麻烦 |
| Python sidecar (sqlglot) | 跨进程通信，复杂度高 |

**选 JSqlParser**（已可能在 dts-platform 中存在；如未存在，加依赖到 `dts-platform/pom.xml`）。

### 核心 API

```java
public class SqlColumnLineageExtractor {

    public ColumnLineageGraph extract(
        String compiledSql,
        String dialect,           // postgres/spark/hive
        Map<String, List<String>> upstreamSchemas  // tableFqn -> columns
    );

    record ColumnEdge(
        String upstreamTable,
        String upstreamColumn,
        String downstreamColumn,
        TransformType transformType,
        String expression,
        Confidence confidence
    ) {}
}
```

### 解析规则（最小集）

| SQL 形式 | 行为 | TransformType | Confidence |
|---|---|---|---|
| `SELECT a FROM t` | (t.a, _output_.a) | IDENTITY | HIGH |
| `SELECT a AS b FROM t` | (t.a, _output_.b) | ALIAS | HIGH |
| `SELECT a+b AS c FROM t` | (t.a, _output_.c), (t.b, _output_.c) | EXPRESSION | HIGH |
| `SELECT SUM(a) AS s FROM t` | (t.a, _output_.s) | AGGREGATE | HIGH |
| `SELECT CASE WHEN a>0 THEN b ELSE c END AS x FROM t` | 3 条边 -> _output_.x | CASE | HIGH |
| `SELECT * FROM t` | upstreamSchemas[t] 全列 -> _output_.同名 | IDENTITY | MEDIUM |
| `WITH cte AS (SELECT a FROM t) SELECT a FROM cte` | t.a -> _output_.a（CTE 透传） | IDENTITY | HIGH |
| `SELECT t1.a, t2.b FROM t1 JOIN t2 ...` | 各自独立 | IDENTITY | HIGH |
| 含 UDF / 自定义函数 | 上游列 -> 输出列 | EXPRESSION | LOW |

### 不支持范围（本 Sprint 留空）

- LATERAL VIEW
- 嵌套 STRUCT/ARRAY 列
- MERGE INTO 的 UPDATE SET
- 动态 SQL

这些情况返回 confidence=LOW + notes 标记。

### 错误处理

- SQL 解析失败：日志 WARN，返回空 graph，调用方决定是否回退到表级
- 上游列不在 schema 中：边不生成，加入 `unresolved` 警告列表

## 影响范围

- 新增 `dts-platform/.../service/catalog/lineage/SqlColumnLineageExtractor.java`
- 新增 `dts-platform/.../service/catalog/lineage/dto/ColumnLineageGraph.java`
- 修改 `dts-platform/pom.xml` —— 引入 jsqlparser
- 大量单测 fixtures：`dts-platform/src/test/resources/sql-fixtures/column-lineage/*.sql`

## 验证

- [ ] 单测覆盖上述 9 种规则各 ≥ 2 个 fixture
- [ ] 性能基准：解析 100 条 50 行 SQL < 5s
- [ ] 失败 SQL（语法错误）不抛异常，返回空 graph + 错误日志
- [ ] `SELECT *` 时上游 schema 缺失返回 confidence=LOW 列表
- [ ] CTE 链式（多层 WITH）正确透传

## 完成标准

- [ ] 解析器实现 + 单测
- [ ] 性能基准达标
- [ ] 边界案例（解析失败、`*`、未知 UDF）有兜底
