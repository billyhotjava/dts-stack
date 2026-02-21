# P0-07 NL2SQL 安全校验与自修复重试链路

`status`: `in-progress`  
`priority`: `P0`  
`inspiration`: `只读 SQL 护栏 + 自动纠错重试`

## 目标

把 NL2SQL 的“生成 SQL”升级为“安全可执行 SQL”：先校验，再执行，失败可重试修复，最终给到可读错误。

## 子任务

1. SQL 安全护栏
- 统一只读校验：仅允许 `SELECT/WITH`，禁止多语句与危险关键字。
- 结果返回结构化安全判定（safe/blocked/reason）。

2. 执行前预检
- 执行前检查 SQL 长度、占位符完整性、基础语法结构。
- 预检失败直接返回可读错误，不进入执行层。

3. 自动纠错重试
- 首次失败后基于错误信息进行 1-2 次定向重写重试。
- 记录每次重试输入/输出与结果，纳入审计。

4. 失败分级
- 错误分类：`syntax/permission/schema/timeout/runtime`。
- 前端统一展示短错误码 + 可读提示。

## 当前进展（2026-02-21）

- [x] 大屏 AI 生成链路新增 `nl2sqlDiagnostics`，并对 `sqlBlueprints` 输出 `safetyStatus/safetyReasons`。
- [x] 执行链路补充模板变量预检：未解析 `{{ }}` / `${ }` 直接失败，避免落入执行层。
- [x] `QueryExecutionFacade` 增加结构化安全检查对象（用于后续错误分类与前端可读提示）。
- [x] `GlobalExceptionHandler` 已对常见 SQL 预检错误映射为稳定错误码（如 `SQL_READ_ONLY_REQUIRED`）。
- [x] 执行链路接入 native SQL 自动纠错重试（默认 1 次，语法型错误触发）。
- [x] 自动纠错策略已扩展（覆盖全角标点、重复逗号、括号前多余逗号、更多子句前逗号场景）。
- [x] 前端已接入 `SQL_*` / `DB_*` 错误码的人话提示（ErrorNotice/数据源/查询页）。

## 验收标准

- 危险 SQL 误放行 = 0。
- 具备自动重试后，可执行率相对基线提升（目标 +8% 以上）。
- 失败响应可读，不再只返回原始数据库异常。

## 风险与回滚

- 风险：重试逻辑引入额外延迟。  
- 回滚：将重试降级为可配置开关，默认最多 1 次。

## 实现难度评估

- 难度：`中高`
- 预计周期：`1.5 ~ 2 周`

## 前置依赖

- `P0-06-nl2sql-eval-feedback-loop.md`
- `P1-01-datasource-unification-sql-mode.md`
