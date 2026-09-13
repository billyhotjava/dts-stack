# T02: 派生指标编译到 SQL + measure-of-measures 展开

**优先级**: P0
**状态**: READY
**依赖**: T01, F3/T03

## 目标

把 T01 产出的 AST 翻译成 SQL 片段，正确处理**measure-of-measures 的聚合层级展开**——派生指标引用原子指标时，必须先在原生粒度各自聚合，再在派生层做算子。

## 核心算法

### 1. 两阶段模型

```
阶段 A：识别所有 atomic references 及其 native model
阶段 B：把派生指标 expression 翻译成"对预聚合结果做算子"的 SQL
```

派生指标编译器不直接产 FROM/JOIN——那是 MultiModelCompiler + SymmetricAggregateRewriter 的职责。派生指标**只贡献 SELECT 子句的一列**。

### 2. AST → SQL 片段 映射

| AST 节点 | SQL |
|---|---|
| `Literal(42, number)` | `42` |
| `Literal("x", string)` | `'x'` |
| `Reference(["revenue"])` | `<resolved>.revenue` — 从符号表拿（见 3） |
| `BinaryOp("+", l, r)` | `(<l> + <r>)` |
| `BinaryOp("/", l, r)` | `CASE WHEN <r>=0 THEN NULL ELSE <l>/<r> END` |
| `UnaryMinus(x)` | `(-<x>)` |
| `FunctionCall("ratio", a, b)` | 同 `/` |
| `FunctionCall("coalesce", a, b)` | `COALESCE(<a>, <b>)` |
| `FunctionCall("if", c, t, e)` | `CASE WHEN <c> THEN <t> ELSE <e> END` |
| `FunctionCall("growth", r, n)` | 需要 time_dimension 上下文，展开成 `(<r> - LAG(<r>, <n>) OVER (...)) / LAG(<r>, <n>) OVER (...)` |
| `FunctionCall("share", r, dim)` | `<r> / SUM(<r>) OVER (PARTITION BY <dims except dim>)` |

### 3. 符号表（SymbolTable）

编译时传入：
```java
public record SymbolTable(
    String baseModel,
    List<JoinResolved> joins,
    Map<String, ResolvedAtomicMetric> atomicRefs,      // "revenue" → 预聚合后的列名
    Map<String, ResolvedDimension> dimensionRefs,
    Map<String, ExprNode> otherDerivedRefs             // derived.b → AST（供递归）
) {}
```

`ResolvedAtomicMetric` 已经是**经过 SymmetricAggregateRewriter 处理后在 outer query 的列名**，派生指标编译器不必再关心 fanout。

### 4. 递归展开

```
derived.[a] = [revenue] / [order_count]
derived.[b] = derived.[a] * 100
```

编译 `derived.[b]`：
1. 从 symbolTable.otherDerivedRefs 拿到 `derived.[a]` 的 AST
2. 递归编译得 `(revenue / order_count) * 100`（带 CASE 除零保护）
3. 深度限制 3（T01 检查）

### 5. 类型感知

编译时保留类型信息：
- number → float64 / decimal
- string → varchar
- date → date / timestamp
- boolean → boolean

类型冲突在 T01 就会报错，本 Task 不重做但允许运行时类型 cast（如 date → varchar 用于 `concat`）。

### 6. 与 fanout 的协调

`SymmetricAggregateRewriter.rewrite(request)` 返回：
- outer SELECT 列集合（每个 atomic metric 对应一个 outer expression）
- CTE 列表
- 维度表达式

派生指标 SELECT 列通过 `ExpressionToSqlCompiler.compile(ast, symbolTable)` 产出，**插入到 outer SELECT**，引用的是 outer expression 别名。

流程：
```
MultiModelCompiler
    ↓ 识别 fanout
SymmetricAggregateRewriter
    ↓ 产 outer SELECT 列（仅 atomic）
ExpressionToSqlCompiler            ← 本 Task
    ↓ 基于 outer 列 生成 derived 表达式
最终 SQL
```

### 7. 测试矩阵（含 fanout）

| 场景 | 手工参考 SQL | 自动产出 SQL 校验 |
|---|---|---|
| 单表简单比例 `[付费率]` | SELECT SUM(paid_cnt)/SUM(total) FROM ... | ✓ |
| 单表 with filter 条件 | ... WHERE region='华东' 再比例 | ✓ |
| N:1 join 下的比例 | JOIN 后 outer SUM 两个 measure 再除 | ✓ |
| 1:N fanout 下的比例 | 预聚合 CTE × 2 + outer 除 | ✓ |
| growth 函数 + time | 窗口函数 LAG | ✓ |
| share 函数 | 窗口函数 SUM OVER PARTITION | ✓ |
| 嵌套 derived 3 层 | 递归展开 | ✓ |
| derived 循环依赖（被 T01 拦） | 拒绝 | ✓ |

### 8. 不处理的场景（本 Task）

- AVG 作为 derived 的子表达式 —— 同 fanout + AVG 一样拒绝
- `cumulative` 函数（grammar 保留，编译本 Sprint 不实现）
- `moving_avg`（同上）

列入错误表：`DSL_E050` Function not implemented in Phase 1，明确返回提示。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/expression/ExpressionToSqlCompiler.java` |
| 新建 | `service/semantic/expression/SymbolTable.java` |
| 新建 | `service/semantic/expression/DerivedMetricResolver.java`（拓扑排序 + 递归展开） |
| 修改 | `MultiModelCompiler` 支持 request 里带 derived_metrics |
| 修改 | `SymmetricAggregateRewriter` 暴露 outer alias 信息供本模块使用 |
| 测试 | 15+ 用例，重点 fanout + derived 组合 |

## 验证

- [ ] 5 个 F1/T03 样例全部编译出与手工 SQL 等价的代码（结果一致）
- [ ] fanout 矩阵 4 个场景（见 7. 表格）结果正确
- [ ] growth + share 两个窗口函数样例执行结果正确
- [ ] 嵌套 derived 3 层正确展开
- [ ] 除零、null、类型异常都安全处理
- [ ] 不支持的函数返回 DSL_E050 明确提示

## 完成标准

- [ ] 派生指标能在 Card Editor 端到端创建并查询（本 Task 保证后端能力）
- [ ] SQL 与手工参考 SQL 的行为差异为 0（数据一致）
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 文档 `assets/specs/07-derived-compilation.md` 完整描述算法
