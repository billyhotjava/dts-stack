# Sprint-87：平台数据架构与跨域契约实施

**时间盒**：2026-08-24 ～ 2026-09-04（10 个工作日）

**状态**：IN_PROGRESS / BLOCKED_ACCEPTANCE（F0～F6 已完成本地非 E2E 验证；后追加 F7 待另一实施 session 编码）

**类型**：Implementation / Compatibility Migration / UI Productization

**目标**：在保留旧 API、字段和深链可回滚的前提下，让平台架构字典拥有唯一写边界，让模型批量/二次物化、物理资产状态和指标业务上下文使用同一组稳定 ID 与版本证据；同时通过 F7 将业务分类、数据域、来源系统和业务过程收敛为“数据域优先、默认上下文自动派生”的建模流程，并通过真实菜单和 Chrome 95 验收。

## 1. 开工边界

本 Sprint 只消费 Sprint-86 已批准 ADR，不重新发明业务语义。原计划要求 F0/T01 关闭后编码；经用户明确授权，源码实施和本地非 E2E 验证已先行完成，但该授权不替代以下发布与真实验收输入：

- 客户/生产域、资产、SOURCE/DIM、指标、批量和增长画像；
- GitNexus 索引成功刷新及逐符号 impact；
- 目标环境登录、菜单点击、Chrome 95、审计查询基线；
- 迁移 dry-run 样本、备份和批次回滚锚点；
- Sprint-86 IT-01～07 已通过，不再构成本 Sprint 阻塞；实现必须忠实消费批准结论。

## 2. 端到端契约链

| 层 | 目标落点 | 关键契约 |
|---|---|---|
| UI | `/data-architecture`、模型 Table、资产台账、指标编辑器 | 菜单可达、分页 10、四态、显式多选、旧深链无损跳转 |
| API | 既有 domains、release-candidates、assets-v2、indicators | 兼容扩展；Idempotency-Key/ETag；稳定 ID；不新增平行控制面 |
| Service | 唯一架构 command/read boundary；候选/物化；资产/统计；指标版本 | 方案 A guard、DAG、正交状态、兼容双读写、审计 |
| Data | 现有字典/ModelSpec/Candidate/CatalogDataset/Indicator 表 + Expand 结构 | 不可变 revision/observation；Producer/Evidence；统计投影；migration issue |
| Migration | preview → apply(batch) → rollback(batch) | 自动回填只接受唯一匹配；旧字段与路由首轮不删除 |
| Verification | unit/IT/source-contract/Chrome95/真实菜单 E2E | 编码全部完成后集中执行一次；证据分别标代码、部署、真实验收 |

## 3. Context Ledger

本 Sprint 不重复扫描 Sprint-86 已登记事实，直接消费：

- `../sprint-86-202608-data-architecture-control-plane/README.md` L01～L32；
- `../sprint-86-202608-data-architecture-control-plane/assets/f1-t02-decision-pack.md` 的关系、DAG、candidate/attempt 契约；
- `../sprint-86-202608-data-architecture-control-plane/assets/f2-f3-data-contract-pack.md` 的资产/指标/NFR 契约；
- `../sprint-86-202608-data-architecture-control-plane/assets/information-architecture-blueprint.md` 的目标路由与页面走查；
- `../sprint-86-202608-data-architecture-control-plane/assets/implementation-roadmap.md` 的 Expand/Contract、回滚和观测门禁。

新增事实只能追加本 Sprint 的 `assets/implementation-baseline.md`，不得静默覆盖 Sprint-86 架构事实。

## 4. Gate Registry

| Gate | 项目 | 状态 | 证据 | 关联 Task |
|---|---|---|---|---|
| G0 | 交付与登录基线 | BLOCKED | `it/baseline.md` | F0/T01 |
| G0 | 客户/生产画像 | BLOCKED | 待 `assets/implementation-baseline.md` | F0/T01 |
| G0 | DTS 不变量 | PASS（设计） | Sprint-86 ADR；A3/A4/B1/D1/D4 | - |
| G1 | 契约链 | PASS（架构设计） | Sprint-86 IT-03/04 与关系契约已批准；运行契约测试仍由各实施 Task 执行 | - |
| G1 | NFR | PASS（设计预算） | Sprint-86 `assets/nfr-budget.md` 已批准；运行 fitness functions 仍由各实施 Task 执行 | - |
| G2 | 影响分析 | PARTIAL_PASS | 逐符号 impact 已执行；detect_changes=LOW；新增文件/前端索引假阴性仍由测试与构建兜底 | F0/T01 |
| G3 | 发布安全 | PASS（方案） | Sprint-86 `assets/implementation-roadmap.md` 已批准；发布演练仍由 F6/T01 执行 | F6/T01 |
| G4 | 可运维/真实验收 | BLOCKED_E2E_DEPLOYMENT | 本地集中验证通过；未部署、未执行真实菜单 E2E | F6/T01 |

## 5. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与生产画像 | 1 | P0 | BLOCKED_INPUT |
| F1 | 架构字典控制边界 | 1 | P0 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E |
| F2 | 模型关系与批量物化 | 1 | P0 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E |
| F3 | 资产语义与统计投影 | 1 | P0 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E |
| F4 | 指标上下文迁移 | 1 | P0 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E |
| F5 | 信息架构与路由收敛 | 1 | P1 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E |
| F6 | 集中验证与发布观测 | 1 | P0 | PARTIAL_LOCAL_VERIFY_PASS |
| F7 | 建模规划上下文简化 | 1 | P0 | IN_PROGRESS |

**实施顺序**：原实施链 F0 → F1 → F2/F3 → F4 → F5 → F6 已完成本地验证。后追加链为 F7/T01 → F6/T01 定向回归与真实验收；F7 必须复用 F1 的唯一字典 owner、F4 的指标上下文契约和既有规划策略 API，不新建平行台账。E2E 仍在本轮新增编码全部完成后集中执行一次。

## 6. 追溯矩阵

| Sprint-86 决策 | Sprint-87 Task | 主要证据 |
|---|---|---|
| ADR-86-08/17 | F1/T01 | command dependency rule、负向授权、审计 |
| ADR-86-13/14/15 | F2/T01 | revision/DAG/candidate/attempt IT、模型 Table source-contract |
| ADR-86-04/05/16/18/19 | F3/T01 | dry-run、CatalogAssetKey、状态/统计容量 IT |
| ADR-86-06/07 | F4/T01 | 逐消费者兼容测试、迁移 issue、发布 E2E |
| ADR-86-09 | F5/T01 | 菜单种子、旧路由映射、Chrome95、菜单点击 E2E |
| ADR-86-10 | F0/T01、F6/T01 | 备份、批次回滚、观测、Contract NO-GO/GO |
| 2026-08-10 建模简化决策 | F7/T01 | 规划策略扩展、默认分类/过程、来源映射复用、引用保护与 UI 旅程 |

## 7. Sprint Definition of Done

- [ ] F0～F7 全部 DONE，任何 BLOCKED/GAP 均关闭或从 Sprint 目标中显式移除。
- [ ] 迁移 preview/apply/rollback、代码测试、构建、部署和真实菜单 E2E 均有独立证据。
- [ ] Chrome 95 下空/加载/错误/成功和批量/二次物化旅程通过。
- [ ] 新旧 API/字段/路由在观测期内可回滚；首轮没有未经审批的删除。
- [ ] 权限负向测试证明部门角色不能写平台全局架构字典。
- [ ] `gitnexus_detect_changes()` 证明没有平行 owner 或超范围执行流。
- [ ] 未将 CODE_COMPLETE、DEPLOYED 或 fixture E2E 冒充 REAL/DELIVERED。

## 8. 非目标

- 不实现通用 MDM；只保留 Sprint-86 冻结的接口边界。
- 不实现多租户产品 UI 或伪 tenant 隔离。
- 不把所有页面统一为 Table；层级架构对象仍保留树/分组。
- 不在首轮切换中删旧表、旧列或旧路由。

## 9. 当前实施证据

源码、测试、构建、影响检查和未执行边界见 `assets/implementation-evidence-20260810.md`。当前判定只到 `CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E`，不等同于部署或真实交付。
