# Sprint-35: dts-metrics 数据仓库可视化设计重构（202605）

**时间**: 2026-05
**状态**: IN_PROGRESS
**类型**: Architecture / Productization / Implementation Plan（dts-metrics-webapp + dts-metrics + dts-platform + dbt gateway）
**目标**: 基于 Sprint-32 的 React Flow 指标工作台方案，补齐“源数据库清洗到数据仓库之后，dts-metrics 从哪一层开始进行可视化设计”的硬边界，并按“架构与 PRD -> 前后端 API -> 前端 -> 后端 -> 安全与评审”顺序拆成可执行 feature/task。

## Sprint-32 评审结论

Sprint-32 的服务边界方向正确：`dts-metrics` 负责指标语义、graph draft、metric-pack、受控 DSL 和候选 DWS/ADS artifact；`dts-platform` 继续持有 IAM、资产、权限、RLS、审计、审批、dbt 发布网关、BI Dataset 和血缘事实源。

需要重构的问题是：Sprint-32 仍把“React Flow 画布”“指标公式”“DWS/ADS artifact”“发布闭环”并列推进，但没有把 ELT 分层入口写成不可误解的产品契约。Sprint-35 必须先定义数据层入口，再设计 API、UI、后端和安全验收，避免只停留在功能名列表。

## 分层决策

`dts-metrics` 的默认可视化入口不是 ODS/STG，也不是任意 DWD 明细表，而是 platform Catalog 中已发布、已治理、可授权读取的 DWS/ADS 资产。

| 数据层 | 现有职责 | dts-metrics 角色 | 是否默认进入可视化 |
|--------|----------|------------------|--------------------|
| 源数据库 | 业务系统原始表 | 只用于血缘、来源说明和影响分析 | 否 |
| ODS | Addax/Airflow 落地后的原始快照或轻度结构化表 | 只作为 lineage 上游和质量缺口提示 | 否 |
| STG | dbt 源适配、类型转换、字段重命名 | 只作为 dbt 证据和调试层 | 否 |
| DWD | 清洗后的业务明细事实、标准码、字段 contract | 高级建模入口：用于生成新的 DWS 候选模型；可 drill-down，但不可默认面向看板消费 | 有条件，不默认 |
| DWS | 主题域/业务对象粒度的汇总服务层 | 默认指标建模和可视化入口：业务对象、维度、指标、Join 优先从这里选择 | 是，主入口 |
| ADS | 应用/看板消费层 | 可作为已有消费资产导入，也可作为发布结果；不建议作为新指标口径的唯一真源 | 是，消费/复用入口 |
| BI Dataset / 大屏 | 面向消费的发布对象 | 发布后注册和下游锁定，不承载建模事实源 | 否，消费出口 |

**规则**:

- 默认路径：源库 -> Connector Center -> ODS -> dbt STG/DWD/DWS/ADS -> Catalog 资产事实源 -> `dts-metrics` DWS/ADS 可视化建模 -> platform/dbt 验证发布 -> BI Dataset/血缘/大屏消费。
- 新指标或新主题模型优先基于 DWS。如果缺 DWS，才在“高级建模”里选择一个或多个 DWD 明细资产生成候选 DWS，之后再进入指标可视化。
- ADS 可被读取为“已有应用层资产”，但新口径不能只从 ADS 反推，必须能追溯到 DWS/DWD 和 dbt manifest 证据。
- DWD/DWS/ADS 必须来自 platform `warehouseLayer`、schema contract、lineage、governance gap、asset permission 和 RLS/masking 策略。`dts-metrics` 不直接解析 dbt manifest，也不直接读 dbt 文件系统。

## Feature 顺序

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | 整体架构与 PRD 契约 | P0 | 5 | READY | Sprint-31, Sprint-32 |
| F2 | 前后端 API 契约 | P0 | 5 | IN_PROGRESS（T01/T02 DONE；T03-T05 IN_PROGRESS） | F1 |
| F3 | 前端可视化工作台 | P0 | 5 | IN_PROGRESS（T03 DONE；T01/T02/T04/T05 IN_PROGRESS） | F1, F2 |
| F4 | 后端建模与 dbt 网关 | P0 | 5 | IN_PROGRESS（T01/T03 DONE；T02/T04 IN_PROGRESS） | F1, F2 |
| F5 | 安全、评审机制与 IT 准入 | P0 | 5 | IN_PROGRESS（T03 DONE） | F1-F4 |

**统计**: READY=10, IN_PROGRESS=9, DONE=6, BLOCKED=0

## 完成标准

- [ ] 架构文档明确 DWD/DWS/ADS 在 `dts-metrics` 中的入口、用途、限制和验证证据。
- [ ] API 契约覆盖可视化资产查询、graph draft、DSL preflight、候选 artifact、platform/dbt validation、发布 dry-run 和审计事件。
- [ ] 前端工作台可以按数据层筛选资产，默认引导用户从 DWS/ADS 建模，并把 DWD 入口放在高级建模流程。
- [ ] 后端只保存 metrics 领域事实，所有资产、权限、RLS、审计、dbt 发布和 BI/lineage 注册都通过 platform 契约完成。
- [ ] 安全评审能证明：无任意 SQL 默认入口、无 platform 表直读、无 dbt 凭据外泄、无绕过 RLS/masking 的预览或发布。
- [ ] IT 证据覆盖 DWS 默认入口、DWD 生成 DWS 候选、ADS 导入复用、验证失败定位、发布 dry-run、回滚和旧 semantic 兼容。

## 非目标

- 不在 Sprint-35 引入完整 cube 缓存、cost-based optimizer、GraphQL/OData 或向量指标搜索。
- 不把 DWD 明细开放成普通看板拖拽入口；DWD 只服务高级建模和受控 drill-down。
- 不把权限、审批、审计、dbt 运行、资产目录事实迁入 `dts-metrics`。
- 不默认执行历史 `semantic_*` 生产迁移，只保留 dry-run、映射报告和兼容代理设计。

## 评审机制

Sprint-35 执行必须按 `assets/review-mechanism.md` 逐级过门：

1. 架构评审：先确认 ELT 分层入口和事实源边界。
2. API 评审：确认 URL、DTO、错误码、权限上下文和降级语义。
3. 前端评审：确认页面动作都接真实 API，不使用静态假数据替代主链路。
4. 后端评审：确认 graph/DSL/artifact/status 只落 metrics 事实，platform 控制面不被复制。
5. 安全评审：确认 RLS/masking/audit/release gate 在预览、验证、发布阶段一致。

## 相关材料

- Sprint-32 原始方案: `worklog/v2.2.3/sprint-32-202605/README.md`
- Sprint-32 评审: `worklog/v2.2.3/sprint-35-202605/assets/sprint-32-review.md`
- ELT 分层 PRD: `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-elt-layer-prd.md`
- API 契约: `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-api-contract.md`
- 评审机制: `worklog/v2.2.3/sprint-35-202605/assets/review-mechanism.md`
- IT 计划: `worklog/v2.2.3/sprint-35-202605/it/README.md`
