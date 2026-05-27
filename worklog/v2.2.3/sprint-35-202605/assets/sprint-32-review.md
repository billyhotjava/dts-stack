# Sprint-32 内容评审

## 结论

Sprint-32 的服务拆分和 React Flow 工作台方向成立，但作为后续实施 sprint 仍不够硬。主要问题不是缺功能名，而是缺少围绕 ELT 分层的产品契约：`dts-metrics` 究竟从 DWD、DWS 还是 ADS 开始建模，哪些层只能看、哪些层可以生成、哪些层可以发布，没有变成 feature/task 的准入条件。

## Findings

| 严重级别 | 问题 | 证据 | Sprint-35 处理 |
|----------|------|------|----------------|
| P0 | 未明确默认可视化入口层 | Sprint-32 写“业务对象、数据资产、Join、指标、DWS/ADS、发布都在图上完成”，但没有声明 DWD/DWS/ADS 的入口优先级 | Sprint-35 固化：默认从已发布治理的 DWS/ADS 进入；DWD 只作为高级建模上游 |
| P0 | Feature 顺序混合 API、UI、后端与安全 | Sprint-32 F1-F6 按能力块排序，API、前端、后端和安全检查分散在多处 | Sprint-35 改为架构/PRD -> API -> 前端 -> 后端 -> 安全/评审 |
| P0 | DWS/ADS 生成缺少 DWD 约束 | F4 只写“已验证指标组合成同粒度 DWS/ADS”，未说明何时允许从 DWD 生成 DWS | Sprint-35 增加 DWD 选择准入、粒度校验、标准码和 lineage 证据 |
| P1 | API 契约不够可实现 | `react-flow-metrics-contract.md` 列了端点，但缺少层级过滤、资产 contract、状态机 DTO 和错误码 | Sprint-35 增加 `visual-assets`、graph、preflight、validation、publish dry-run DTO |
| P1 | 前端验收容易继续停留在画布展示 | Sprint-32 完成标准强调 React Flow 节点，但没有要求 DWS/DWD/ADS 层级体验差异 | Sprint-35 前端任务要求层级导航、DWS 默认引导、DWD 高级建模、ADS 复用和验证定位 |
| P1 | 安全散落在权限、RLS、审计、发布任务里 | 没有独立安全评审门禁，容易只在发布阶段才发现策略不一致 | Sprint-35 F5 独立安全、评审和 IT 准入 |

## 保留的 Sprint-32 决策

- `dts-metrics` 仍是指标语义产品事实源。
- `dts-platform` 仍是企业控制面事实源。
- 模型检测和发布必须通过 platform/dbt gateway。
- metric-pack 和画布生成的是候选 artifact，不直接写生产 dbt 目录。
- 旧 `/api/semantic/**` 只做兼容代理、dry-run 或明确弃用，不再扩展 platform 内部语义事实源。

## Sprint-35 新增硬约束

1. 所有可视化资产必须有 `warehouseLayer`，且值必须能区分 `DWD`、`DWS`、`ADS`。
2. `DWS` 是默认建模入口；`ADS` 是消费复用入口；`DWD` 是高级生成入口。
3. `DWD -> DWS` 候选模型必须通过粒度、主键、标准码、字段级血缘和 RLS/masking 检查。
4. `DWS/ADS -> 指标可视化` 必须通过 asset permission、schema contract、治理缺口和 release gate 状态检查。
5. 前端、后端、API、安全评审都必须有对应 IT evidence 目录，不能只写功能描述。
