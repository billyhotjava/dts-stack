# Sprint-7: 旁路区 + 全局搜索 + 收尾

**时间**: 2026-06
**状态**: READY
**目标**: 收编平台级旁路区（服务/治理/安全/运维/设置）+ 全局搜索 ⌘K（跨阶段+旁路），并完成端到端黄金主线走查与 Chrome 95 全量回归冒烟，让原型整体可交付。

## 背景

依据设计文档 [§5 全量 IA 映射](../2026-06-19-dts-platform-unified-elt-redesign-design.md) 与 [sprint-queue.md](../sprint-queue.md)，黄金主线四阶段（连接→集成→资产→指标）已由 S1–S6 落地。本 sprint 收编**平台级旁路区**——它们不在线性主线里，但通过左轨分隔线下的「平台」折叠区随时可达：

- **服务 Serve**：`ApiServices` · `DataProducts`(服务视角) · `BiLinks` · `Tokens` · `BusinessConsumption`
- **治理 Govern**：`GovernanceCenter` · `PermissionAudit` · `QualityRules`(管理) ·（指标治理视角已在 S6 阶段④收口，此处仅留治理入口）
- **安全 Security**：`data-security` · `DatasetAccessApproval`
- **运维 Ops**：`OpsOverview` · `Instances` · `Backfill` · `AlertLog` · `LogCenter` · `ReleaseGovernance` · `PlatformEventObservability` · `AuditEvidence`
- **设置 Settings**：`settings` · `sys`

**旁路区原型策略**（设计文档 §11「先占位后充实」）：每个旁路页面至少有**可点入口 + mock 列表骨架**，允许先占位；不要求现网全部字段细节。

本 sprint 同时承担**收尾职责**：全局搜索 ⌘K 跨阶段+旁路命中跳转、端到端黄金主线连贯性走查、Chrome 95 全量回归冒烟、视觉一致性核查。依赖 **S1–S6**（收尾需全主线就位）。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| [F1](./features/F1-数据服务/README.md) | 数据服务 | 2 | READY | P1 |
| [F2](./features/F2-治理与安全/README.md) | 治理与安全 | 2 | READY | P1 |
| [F3](./features/F3-运维/README.md) | 运维 | 2 | READY | P1 |
| [F4](./features/F4-设置与全局搜索与打磨/README.md) | 设置 + 全局搜索 + 打磨 | 3 | READY | P0 |

**统计**: Task 总数 9 ｜ READY=9

## 约束基线（全 sprint 适用）

- **Chrome 95**：禁用 oklch / `:has()` / 容器查询 / subgrid；构建必开 `@vitejs/plugin-legacy`（chrome>=95）。CSS 源码层手写 HSL/hex token。**本 sprint 收尾含 Chrome 95 全量回归冒烟**（见 F4-T03 与 [it/README.md](./it/README.md)）。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关；不接真后端。本 sprint 新增/复用 `apiServicesService`、`opsService` 等旁路域 service。
- **旁路区策略**：可「先占位后充实」，但每页至少**可点入口 + mock 列表骨架**。
- **设计系统**：Swiss 网格、HSL token、CompactTable 默认 10 条/页（切换条数刷新、`pageSize` 收敛）。
- **命名对齐**：页面/组件/service 命名对齐现网 `dts-platform-webapp`。
- **全局搜索**：⌘K 跨阶段+旁路，命中后跳转到对应页面/阶段。

## 完成标准

- [ ] 旁路区五大域（服务/治理/安全/运维/设置）所有页面在左轨「平台」折叠区有**可点入口**，路由可达。
- [ ] 每个旁路页面至少呈现 mock 列表骨架（CompactTable，默认 10 条/页），经对应 `*Service.ts` 取数。
- [ ] 全局搜索 ⌘K 可唤起，索引覆盖四阶段 + 五旁路域，命中项可跳转到目标页面/阶段。
- [ ] 端到端黄金主线走查通过：从项目门户「下一步」引导卡可连贯走完 连接→集成→资产→指标（样例项目「销售准备项目」）。
- [ ] Chrome 95 全量回归冒烟通过：legacy 构建产物可加载，全主线 + 旁路区无 oklch/`:has()`/容器查询/subgrid 报错。
- [ ] 视觉一致性核查通过：旁路区沿用 Swiss token、状态点语言、CompactTable 密度与主线一致。
- [ ] 通过 [it/README.md](./it/README.md) 列出的端到端验证项（含黄金主线走查 + Chrome 95 回归 + 全局搜索）。
