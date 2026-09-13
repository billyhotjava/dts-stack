# T03: 派生指标 DSL BNF + 函数白名单

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

定义一个**极小的表达式 DSL**，让分析师在 Card Editor 里用"公式 + 引用"方式创建派生指标，不暴露 SQL。DSL 必须：
- 表达力足以覆盖 90% 派生指标场景
- 不能表达 SELECT / FROM / 子查询 / 任意函数
- 可静态校验引用完整性、类型匹配、循环依赖
- 可稳定编译成多方言 SQL（PG / Doris / ClickHouse）

## 交付物

`worklog/v2.2.3/sprint-13-202604/assets/specs/03-derived-metric-dsl.md`

## DSL 设计

### 1. 文法（EBNF 简化版）

```ebnf
expression   ::= term (("+"|"-") term)*
term         ::= factor (("*"|"/") factor)*
factor       ::= literal | reference | function_call | "(" expression ")" | "-" factor
literal      ::= number | string | boolean | null
reference    ::= "[" ident ("." ident)* "]"
function_call::= ident "(" arg_list? ")"
arg_list     ::= expression ("," expression)*
comparison   ::= expression (("="|"!="|">"|">="|"<"|"<="|"in") expression)
condition    ::= "if" "(" comparison "," expression "," expression ")"
ident        ::= [a-zA-Z_][a-zA-Z0-9_]*
```

**没有的东西：**
- 没有 `SELECT` / `FROM` / `JOIN` / `WHERE` / `GROUP BY`
- 没有分号、注释、多语句
- 没有变量赋值、循环、lambda
- 没有字符串拼接运算符（`||`）——用 `concat()` 函数替代（更明确）

### 2. Reference 语法

- `[ads_sales_daily.revenue]` — 引用 model 里声明的原子指标
- `[revenue]` — 引用当前 model 上下文的 metric（省略 model 前缀）
- `[derived._custom_1]` — 引用同一查询内声明的派生指标（`derived.` 前缀保留）
- **所有引用编译期校验**：不存在的 metric/dimension 报错；循环依赖报错；跨密级引用报错。

### 3. 函数白名单

**聚合函数（只能作用在引用上）:**
- `sum(ref)` / `avg(ref)` / `count(ref)` / `count_distinct(ref)` / `min(ref)` / `max(ref)` / `median(ref)`

**时间窗口函数:**
- `lag(ref, n)` — n 期前的值
- `lead(ref, n)` — n 期后的值
- `growth(ref, n)` — `(ref - lag(ref, n)) / lag(ref, n)`
- `cumulative(ref)` — 累计
- `moving_avg(ref, window)` — 移动平均

**比例函数:**
- `ratio(a, b)` — `a / b`（自动处理除零 → null）
- `share(ref, dim)` — `ref / sum(ref) OVER (PARTITION BY <excluding dim>)`

**条件函数:**
- `if(cond, a, b)` — 三元
- `coalesce(a, b, ...)` — 首个非空
- `case_when(c1, v1, c2, v2, ..., else_v)` — 多分支

**格式化（不参与计算，仅影响渲染）:**
- `format_number(ref, pattern)`
- `format_percent(ref)`
- `format_currency(ref, scale)`

**完全禁止：**
- `raw_sql(...)` — 一律拒绝
- 任何不在白名单的函数

### 4. Measure-of-Measures 规则（⚠️ 核心正确性）

派生指标如果引用了 measure，**必须在聚合层级正确展开**。

**错的做法**（naïve）：
```
[付费率] = [付费订单数] / [总订单数]
↓ 直接替换
sum(CASE WHEN paid THEN 1 ELSE 0 END) / sum(1)
```
这在单维度查询时对，但下钻到 `dim_customer.region` 时，如果 base 表有 fanout，分子分母受影响不一致，结果错。

**对的做法**：
- DSL 编译时识别所有 reference 是哪个 measure
- 在 SQL 编译时把每个 measure 各自在原始 model 粒度预聚合
- 派生指标的算子在预聚合后的列上做

**DSL 层保证**：用户写 `[付费订单数] / [总订单数]`，不用关心 fanout，编译器负责展开。

### 5. 类型系统

DSL 有四种类型：`number` / `string` / `date` / `boolean`。

**类型推导规则：**
- `[metric_ref]` → number
- `[dimension_ref]` → 看 dimension.type
- `+` `-` `*` `/` → 两侧必须 number
- 比较运算符两侧必须同类型
- `if(cond, a, b)` → cond 是 boolean, a/b 必须同类型

**不合法例子：**
```
[revenue] + [region]      → ERROR: number + string
lag([region], 1)           → ERROR: lag only on number
```

### 6. 5 个样例（评审用）

```
# 1. 简单比例
[付费率] = [付费订单数] / [总订单数]

# 2. 同比增长率
[同比增长] = growth([revenue], 12)    # 12 个月前的比较（需 time_dimension=month）

# 3. 占比
[区域营收占比] = share([revenue], dim_customer.region)

# 4. 条件聚合
[大单数] = count_distinct(if([amount_cents] > 100000, [order_id], null))

# 5. 嵌套
[大客户付费率] = ratio(
    count_distinct(if([amount_cents] > 100000 and [status]="paid", [order_id], null)),
    count_distinct([order_id])
)
```

### 7. Parser 实现选型

候选：
- **ANTLR4**（Java 侧实现成熟，生成 visitor 代码；跨语言可重用）— **主选**
- 手写递归下降（Kotlin/Java 1000 行以内，灵活但要自己写语法错误提示）
- JParsec / Parboiled2（小众）

推荐 **ANTLR4**：grammar 文件本身就是 spec 的一部分，前端（TS）也可以用同一份 grammar 生成 parser 做 linting（减少"后端通过但前端校验失败"的裂痕）。

### 8. 错误信息规范

所有 parse / semantic error 必须有：
- 错误码（`DSL_E001` ... `DSL_E099`）
- 人类可读描述（中英）
- 错误位置（行/列或字符 offset）
- 修复建议

例子：
```json
{
  "code": "DSL_E012",
  "message_zh": "引用的指标 [nonexistent] 不存在于 base model ads_sales_daily",
  "message_en": "Referenced metric [nonexistent] not found in base model ads_sales_daily",
  "location": { "offset": 12, "length": 13 },
  "hint": "可用指标: [revenue], [order_count], [付费订单数]"
}
```

## Open Questions

1. `cumulative()` / `moving_avg()` 本 Sprint 是否实现？（建议：先放 grammar，实现放 Sprint-14）
2. 是否允许 `derived_metric` 里引用 `dimension`？（建议：允许，因为 `if([region]="华东", ...)` 是常见需求，但 dimension 不能作为被聚合对象）
3. `share()` 的分母如何选 PARTITION BY？（建议：隐式——取当前查询的所有 dimension 减去 `share` 指定的那个）
4. DSL 是否支持自定义命名？（建议：不，派生指标有 `label` 字段单独命名）

## 影响范围

- 新建 spec markdown
- 新建 ANTLR grammar 文件草稿：`assets/specs/03-DerivedMetric.g4`
- 评审会评审

## 验证

- [ ] ANTLR grammar 文件能正确 parse 5 个样例
- [ ] ANTLR grammar 对 5 个反例（含 SQL 注入企图、循环引用、类型错）正确报错
- [ ] 错误码表完整（至少 20 条）
- [ ] open questions 全部 resolution

## 完成标准

- [ ] spec markdown 完成
- [ ] ANTLR grammar 草稿能跑通 5 正例 + 5 反例测试
- [ ] 评审 sign-off
