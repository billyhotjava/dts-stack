# F6：集中验证发布与运维

**优先级**：P0
**状态**：BLOCKED（T01 依赖 F0/T03 的账号/目标画像并缺 Chrome 95；T02 待真实灰度；T03 必须晚于 Expand 稳定）

## 目标

用一次三角色纵向 E2E 证明数据集→分析→看板→受众消费完整闭环，并通过 feature flag、版本化镜像、灰度、回滚、告警和运行手册将变更安全交付。

**范围变更（2026-08-19）**：纵向 governed BI 旅程不改造大屏设计器，但必须用 IT-10 证明历史大屏全链在升级后无断层；IT-11 验证旧 BI 清理只能进入独立 Contract 发布。

## 验收契约

- 以 `../../it/README.md` IT-01～IT-12 为唯一验收清单；证据必须来自实际运行，不预建占位文件。
- Chrome 95 是客户兼容硬门禁；现代 Chrome 可辅助诊断但不能替代。
- 三个职责必须分离：A1 维护、A2 独立发布、A3 消费；另用 A4 做无权限负向。
- 页面、API、数据库、审计、容器和 Git diff 结论分开记录。

## 发布安全

| 项目 | 约束 |
|---|---|
| 开关 | R1 使用 `DTS_ANALYTICS_GOVERNED_BI_ENABLED`；legacy write 在 R1 保持开启，F6/T03 独立关闭 |
| 阶段 | R1 shadow → pilot → default → rollback drill；R2 Contract 另开窗口 |
| 数据库 | Expand-only；先兼容读写/回填/validate，首轮不 contract/drop |
| 镜像 | 保留受影响服务旧 image ID/标签；只重建 platform/analytics/webapp 必要容器 |
| 回滚 | 先恢复 governed flag/旧镜像；Expand 数据保留；本 Sprint 不存在迁移 batch rollback |
| 判定 | 容器 healthy、HTTP、真实账号、状态/checksum、审计、远端 commit 分别验证 |

## 可运维交付物

产物文件（G3/G4 的 Gate 证据，须实际落盘）：

| Gate | 产物路径 | owner |
|---|---|---|
| G3 | `../../assets/release-plan.md` | F6/T02 |
| G4 | `../../assets/runbook.md` | F6/T02 |

- Runbook：contract 502、query 429/504、registration pending、revision drift、public link 异常。
- 指标：query total/error/timeout/429/P95、contract cache hit/stale、publish/reconcile failure、legacy write（用于确认 cutoff 冻结）、unauthorized attempts。
- 告警：5 分钟 error ratio、P95、registration backlog、public link denied spike；阈值以 NFR 校准。
- 日志字段：correlationId/queryId/actor/assetKey/revisionId/datasetVersion/checksum/policyContextHash/outcome/errorCode，禁止 SQL/敏感值。
- 容量：数据集/分析/看板数量、query QPS/并发/行数与缓存内存假设；大屏和迁移容量移交后续阶段。

## UI/UX

- 以页面矩阵和按钮矩阵逐项验收 loading/empty/error/success、键盘、焦点、文案和权限可见性。
- 只运行一条聚焦纵向 journey；失败后按具体断点重跑，不做无目标的全套浏览器循环。
- 发布后保留截图/Network/console/trace；但截图不替代数据库和审计对账。

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 完成三角色 Chrome 95 纵向验收 | P0 | BLOCKED（缺 Chrome 95 与 A1～A4） | F0/T02、F0/T03、F1～F4 |
| T02 | 完成灰度发布、回滚与运维收口 | P0 | IMPLEMENTED（灰度、回滚与观察窗实操待完成） | T01、全部 NFR fitness function |
| T03 | 执行旧 BI 清理与数仓重建发布门禁 | P0 | BLOCKED | T01、T02、F0/T04，Expand 稳定 |

## DoR / 完成标准

- [x] 角色、旅程、证据和发布/回滚框架已定义。
- [x] G3/G4 产物路径已点名（`assets/release-plan.md`、`assets/runbook.md`）。
- [ ] Chrome 95、A1～A4 账号、隔离样本和部署权限可用。
- [ ] 所有 Feature 聚焦测试通过且变更集冻结。
- [ ] IT-01～IT-12、NFR、灰度和 rollback 实际通过。
- [ ] IT-10 大屏 durable set 对账通过；IT-11 证明 cutoff、备份恢复、dry-run 和独立 Contract 门禁齐全。
- [ ] Runbook、告警、日志字段、容量与 on-call owner 完整。
- [ ] `gitnexus_detect_changes()`、模块构建、容器/HTTP/真实账号、git 本地/远端一致性均有证据。
