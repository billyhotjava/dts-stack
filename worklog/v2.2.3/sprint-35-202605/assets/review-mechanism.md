# Sprint-35 评审机制

## 总原则

评审先看边界，再看实现。任何任务如果不能说明“数据来自哪一层、由谁授权、由谁验证、失败如何定位”，不得进入 DONE。

## Gate 1: 架构与 PRD Review

- [ ] 是否明确默认入口为 DWS/ADS。
- [ ] 是否明确 DWD 只作为高级建模上游。
- [ ] 是否明确 ODS/STG 只展示 lineage，不进入普通指标画布。
- [ ] 是否说明 platform 是资产、权限、RLS、审计、dbt 发布事实源。
- [ ] 是否列出非目标，避免把 cube/cache/任意 SQL 拉进本 Sprint。

## Gate 2: API Review

- [ ] API 是否包含 `warehouseLayer`、`assetKey`、`grain`、`permissionDecision`、`governanceStatus`。
- [ ] URL 是否符合资源命名，不用 `getXxx` 这类动词式接口。
- [ ] 错误码是否区分权限、层级、粒度、graph 校验、dbt 校验和 platform 不可用。
- [ ] 前端 API 是否只调用 `dts-metrics`，不直连 platform internal API。
- [ ] service-auth 调用是否使用 `X-DTS-Service` 和 `X-DTS-Service-Token`。

## Gate 3: Frontend Review

- [ ] 默认资产列表是否只展示 DWS/ADS，并把 DWD 放在高级建模入口。
- [ ] 每个画布节点是否展示层级、粒度、权限、治理和验证状态。
- [ ] 前端是否没有静态假数据支撑主链路。
- [ ] 验证失败是否能定位到 node/edge/field/metric。
- [ ] 旧 `/metrics/semantic/**` 入口是否有兼容或迁移说明。

## Gate 4: Backend Review

- [ ] `dts-metrics` 是否只保存 graph/DSL/artifact/status，不复制 platform 事实源。
- [ ] 候选 artifact 是否必须先过 graph preflight、platform contract precheck、dbt validation。
- [ ] DWD -> DWS 是否检查粒度、主键、字段标准码和 lineage。
- [ ] `MetricArtifactGenerationService` 类路径不得持有 dbt 项目目录或数据源密码。
- [ ] 发布结果只保存 platform publish reference，不直接删除或覆盖生产模型。

## Gate 5: Security Review

- [ ] 没有任意 SQL 默认入口。
- [ ] 所有 preview/publish 都带 asset permission check。
- [ ] RLS/masking 在预览、验证、发布阶段使用同一 policy hash。
- [ ] 错误信息不泄露无权资产名称、字段密级或数据源凭据。
- [ ] audit 记录 graph 保存、验证、提交、批准、发布、撤销和回滚。

## Gate 6: IT Admission

- [ ] DWS 默认建模 happy path 有脚本或 Playwright 证据。
- [ ] DWD 高级生成 DWS 候选模型有粒度失败和成功样例。
- [ ] ADS 导入复用有权限和 lineage 证据。
- [ ] platform unavailable 时拒绝生成 artifact。
- [ ] 回滚 runbook 能说明前端路由、graph draft、metric artifact、platform publish record 如何回退。

## GitNexus / Code Review Rule

进入代码实施时，修改任何函数、类或方法前必须按 AGENTS.md 要求做 GitNexus impact analysis；提交前必须执行 GitNexus change detection，确认只影响预期 symbols 和 flows。
