# T01: ControlledMetricDslCompiler 组件移植

**优先级**: P0
**状态**: DONE
**依赖**: F1-T01

## 目标
把 dts-metrics 的受控派生指标编译逻辑移植为平台独立、纯函数、可单测的 `ControlledMetricDslCompiler`。

## 技术设计
新建 `com.yuzhi.dts.platform.service.modeling.ControlledMetricDslCompiler`（@Component，无外部依赖）。移植自 dts-metrics `MetricModelLifecycleService`（v2.2.3）/`MetricCandidateArtifactBuilder`（sprint-35b 分支）的：
- **函数白名单 + 编译**（`compileDerivedExpression`）：sum/count/avg/min/max/count_distinct/ratio/date_trunc/count_if/sum_if/case_when，default → 拒绝。
- **三层防御**：
  1. 黑名单正则 `UNSAFE_EXPRESSION`（`;` `--` `/* */` select/insert/update/delete/drop/alter）；
  2. 函数白名单（switch 默认 reject）；
  3. 标识符 `safeIdentifier`（`[^A-Za-z0-9_]`→`_`）+ `quoteIdentifier`（方言：postgres `"`、doris `` ` ``）。
- 辅助：`requiredArg`/`splitArgs`/`comparisonOperator`（eq/ne/gt/gte/lt/lte）/`literalSql`/`dateTruncSql`（doris 参数顺序差异）。
- 输入：formula（type + 字段/参数）+ dialect；输出：受控 SQL 片段。违规抛 `IllegalArgumentException`（消息含 code 语义，便于上层映射 422）。

实现取向：与 dts-metrics 语义**字节级一致**（黄金 SQL 守护，避免移植漂移）；适配平台 formula JSON 结构（`formulaType` + `formulaJson`，复用平台 `parseFormula`/`readText` 风格或自带解析）。

## 影响范围
- `dts-platform`：新增 `ControlledMetricDslCompiler.java`（service/modeling）。无既有文件行为改动（委托在 T03）。

## 验证
- [ ] 每个白名单函数编译输出与 dts-metrics 对应实现一致（postgres + doris）。
- [ ] 非白名单 type / raw 表达式 / 注入串 → 抛异常。

## 完成标准
- [ ] 组件独立可单测、零外部依赖、`clean compile` 通过（铁律：clean 而非增量）。
