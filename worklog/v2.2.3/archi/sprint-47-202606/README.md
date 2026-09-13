# Sprint-47: 语义层整合 Phase 4 — dts-metrics 退役

**时间**: 2026-06
**状态**: READY（执行 gate：SP-2 列族贯通 + SP-3 原生页平价/路由收敛先完成）
**类型**: Decommission / 破坏性下线（dts-metrics 服务 + dts-metrics-webapp）
**实施分支**: 续 `feat/semantic-consolidation`（或 `feat/dts-metrics-retire`）

## 目标
在平台原生治理页达到工作台平价、路由已收敛（SP-3）后，**安全退役 dts-metrics 服务与 webapp**，消除"两套并行语义层"，整合大计划闭环。原则：**verify-first → 灰度切断 → 保留回退窗口 → 先归档再删**。

## 背景
- 整合决策（[[dts-metrics-elt-ecosystem]]）：dts-platform `SemanticModelingResource` 为唯一权威语义层，dts-metrics 亮点移植后退役。
- 上游：SP-1（Sprint-41，后端受控治理）DONE；SP-2（Sprint-43，语义富化）、SP-3（Sprint-44，前端整合 + 路由收敛）计划就绪。
- dts-metrics 退役足迹（已勘察）：
  - **部署** `docker-compose-app.yml`：`dts-metrics` 服务（:8084）+ traefik 路由 `dts-metrics-api`(`/api/metrics`,prio260) 与 `dts-metrics-ui`(`/metrics`,prio255)；`docker-compose.legacy.yml` 同。
  - **平台侧** `MetricsInternalAccess` / `ServiceDependencyAuthenticationFilter`（metrics 服务 service-auth 授权）、`DtsMetricsCapabilityProperties` + `application.yml`（metrics 能力/iframe href 配置）。
  - **代码** `source/dts-metrics`（后端 8084）+ `source/dts-metrics-webapp`（前端）；`opmanager/` 文档引用。
  - **数据**：v2.2.3 基线 dts-metrics 为内存态（无持久化，[[dts-metrics-branch-baseline]]）→ **无生产数据需迁移**；sprint-35b 的持久化硬化未进 v2.2.3、随退役作废。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 退役前置核验与决策（依赖/流量审计 + 数据处置 + 平价确认）= gate | 3 | READY | P0 |
| F2 | 流量切断与部署下线（traefik 路由 + compose 服务，灰度+回退窗口） | 3 | READY | P0 |
| F3 | 平台侧依赖清理（service-auth 授权 + metrics 配置） | 2 | READY | P1 |
| F4 | 代码归档与整合收口（源码归档 + 分支处置 + 文档/记忆 + 大计划闭环） | 3 | READY | P1 |

**依赖**: 全 sprint gate = SP-3 完成（原生页平价 + iframe 路由已切，Sprint-44 F2/F3）。F2 依赖 F1 gate 通过；F3/F4 依赖 F2 稳定运行一个回退窗口。

## 完成标准
- [ ] F1 核验：确认无 iframe 外的 dts-metrics 消费者、无生产数据需迁移、平台原生页平价达标——出具退役决策。
- [ ] traefik `/api/metrics`+`/metrics` 路由摘除、`dts-metrics` compose 服务停用；灰度可回退。
- [ ] 平台侧 metrics service-auth 授权与配置清理（确认不再需要后）。
- [ ] `source/dts-metrics` + `dts-metrics-webapp` 归档/移除（保留 git 历史）；opmanager 文档更新；整合大计划 SP-1~SP-4 闭环。
- [ ] 退役后全量回归：语义建模全走平台原生页，无死链/无 503。

## 非目标
- 不在平价（SP-3）达成前退役（否则 UX 倒退）——本 sprint 执行 gate。
- 不删除 git 历史（只归档/移出构建，保留可追溯）。
- 不动 dts-platform 语义能力（SP-1~SP-3 已承接）。

## 退役时序与回退（重要）
1. **gate**：SP-3 F2 原生页平价 + F3 路由切原生（灰度 flag 在原生侧）。
2. F1 核验通过 → F2 灰度切断（先停 `/metrics` UI 路由→观察→停 `/api/metrics`→保留服务镜像 N 天回退窗口）。
3. 回退窗口内无异常 → F2 移除 compose 服务、F3 清平台配置、F4 归档代码。
4. **回退**：任一步异常，恢复 traefik 路由 + 重启 dts-metrics 服务（回退窗口内镜像未删）。

## 风险
- 现场旧链接/收藏指向 `/metrics`——靠 SP-3 F3-T01 重定向兜底。
- 平台 service-auth 清理误伤其他内部调用——F3 须精确定位仅 metrics 授权。
- 破坏性操作——每步可回退、先归档、灰度先行。

## 相关材料
- 退役清单/足迹: `assets/sp4-retirement-plan.md`
- 上游: Sprint-41/43/44 README；整合路线图 `worklog/v2.2.3/sprint-41-202606/assets/semantic-consolidation-roadmap.md`
- 集成测试: `it/README.md`
