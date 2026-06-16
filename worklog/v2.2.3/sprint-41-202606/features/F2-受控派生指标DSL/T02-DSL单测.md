# T02: ControlledMetricDslCompiler 单测

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
用单测锁定受控 DSL 的正确性与安全性，作为移植不漂移的守护。

## 技术设计
`ControlledMetricDslCompilerTest`（JUnit5 + AssertJ）：
- **每函数编译**：sum/count/count_distinct/avg/min/max/ratio/date_trunc/count_if/sum_if/case_when，分别断言 postgres 与 doris 输出。
- **方言**：postgres 双引号、doris 反引号；`date_trunc` 的 doris 参数顺序。
- **安全**：
  - raw/custom SQL（含 `select`/`;`/`--`/`/* */`/`drop` 等）→ 抛异常。
  - 非白名单 type → 抛异常（默认拒绝）。
  - 标识符含特殊字符 → 被 `safeIdentifier` 规整后 quote。
- **比较运算符**：eq/ne/gt/gte/lt/lte → `= <> > >= < <=`；非法 op → 拒。

## 影响范围
- `dts-platform`：新增测试类。

## 验证
- [ ] 覆盖全部白名单函数 × 2 方言。
- [ ] 安全用例（注入/raw/非白名单）全部被拒。

## 完成标准
- [ ] 单测全绿（`clean test`）；关键安全断言齐全。
