# 语义层整合路线图（平台侧权威）

**决策日**: 2026-06-16

## 决策
dts-metrics 与 dts-platform `SemanticModelingResource` 是两套并行语义建模层。经评审，**以 dts-platform 为唯一权威语义层**，dts-metrics 亮点移植进平台后逐步退役。

理由：
- 平台 `SemanticModelingResource` 是更成熟系统：完整评审工作流（submit/approve/reject + review-logs）、runs、业务对象 table-mappings、**可用的** publish-dbt / register-bi-dataset / register-lineage、持久化（2 个 Liquibase changelog + 存量数据）、配套前端。
- 平台已具备**物化能力**：`generateArtifacts`→`DbtFileService` 写 dbt 项目树 + `{{ ref }}`（`SemanticModelingService.java:904,1534-1567,1890-1893`），dts-metrics 苦于的"物化桥断裂"在平台侧本不存在。
- dts-metrics（尤其 v2.2.3 基线）仍是较薄原型；其价值在治理"亮点"逻辑（受控 DSL、ELT 分层准入、图形工作台 UX），而非基础设施。

## 生态约束（不可脱离）
dts-metrics/语义层坐落在 ELT + OpenMetadata 生态之上（详见记忆 `dts-metrics-elt-ecosystem`）：
- **Addax**(dts-ingestion) 入湖 → ODS；**dbt**(dts-platform `services/dts-dbt/`) 转换 STG/DWD/DWS/ADS；**Airflow** 编排；**OpenMetadata**(已部署) 结构元数据真源（平台只读镜像 `om_asset_cache`）。
- 治理（分类/owner/域/术语/标准）= 平台本地 DB；血缘双库割裂（ingestion 写 OM、平台 OpenLineage 落本地）。
- 整合**不重造**目录/血缘/编排/转换——这些生态已有；只把"语义建模治理"收口到平台。

## 阶段（→ Sprint 映射与状态）
| 阶段 | Sprint | 状态 | 内容 |
|------|--------|------|------|
| **SP-1** | 41 | ✅ DONE（36 测试绿，已编码） | 受控建模逻辑移植（受控 DSL + ELT 分层准入），后端 |
| SP-2 | 43 | 📋 计划就绪（4F/9T） | 语义富化贯通（dbt meta semantic_type 已存在 → catalog 透出列族） |
| SP-3 | 44 | 📋 计划就绪（4F/15T，**已按现状重写**） | **重建平台原生语义 UI**（替跳转壳）+ 治理呈现 + 工作台 + 路由收敛 |
| SP-4 | 47 | 📋 计划就绪（4F/11T） | dts-metrics 服务/webapp 退役（gated，verify-first/灰度/回退/先归档） |
| 解耦评审 | v2.3 | ⬜ Backlog | dts-platform 按领域缝拆分 |

## 架构边界决策（2026-06-16）
用户提出："dts-platform 单体功能越来越多，不分拆 metrics 架构师会不会有很多问题？" —— 正当关切。结论：
- **消重复 ≠ 解决巨石过载**，是两个独立问题。当前 dts-metrics 是"坏的分拆"（瘦 UI + 重复语义层 + chatty 调回平台 = distributed monolith），合并它**减少**架构债。
- **SP-1 升级为"模块化、可抽取"标准**：受控建模逻辑落进独立 `modeling` 子包/组件（`ControlledMetricDslCompiler`、`EltLayerGate`，显式接口、`semantic_*` schema 命名隔离），**不堆进 2000+ 行 `SemanticModelingService`**——今天是模块，将来可抽取为服务（modular monolith with extraction-ready seam）。
- **dts-platform 按真实领域缝解耦（元数据&目录 / ETL&编排 / 语义建模&服务 / 治理）= v2.3 大版本独立架构议题**，不在本 sprint。metrics 的最终归宿是其中"语义建模&服务"模块，但前提是先合并干净、有清晰契约。
- 抽取判据（满足才独立成服务）：独立有界上下文 + 独立生命周期 + 独立数据 owner + 独立扩展需求 + 独立团队 owner。

## 遗留处置
- **dts-metrics sprint-35b 硬化**（F1 持久化 / F2 安全对等 / F5 拆分 / F6 IT，在 `feat/sprint-35b-dts-metrics-hardening` + `main`）：dts-metrics 既定退役，**不再合入 v2.2.3、不再继续硬化**；移植取其**逻辑**（DSL/分层/图形），非其基础设施。
- 相关记忆：`dts-metrics-branch-baseline`、`dts-metrics-elt-ecosystem`、`dts-metrics-architecture-review`。

---

## 执行交接 / Cold-start（2026-06-16，新会话从这里读起）

**目标**: 新会话可冷启动编码，无需重复勘察。整合大计划 = 在平台 `/api/semantic` 上收口语义建模，退役 dts-metrics。

### 已完成
- **SP-1 (Sprint-41) 后端受控治理已编码并验证**（36 测试绿）：`ControlledMetricDslCompiler`（受控 DSL，独立可抽取）+ `EltLayerGate`（分层闸）+ `governanceMode`（绞杀者开关）+ `buildMetricExpression` 受控委托 + 422/400 错误码。提交 c5234a75→834c755b（已 merge v2.2.3）。

### 关键现状发现（避免重复踩坑）
1. **平台原生语义前端是空壳**：`src/pages/modeling/Semantic*Page` 全是 5 行跳转壳 + `/modeling/semantic-center` 是 iframe，两者都通向 dts-metrics-webapp；`semanticModelingApi.ts`(/api/semantic 客户端)=死代码。提交 `1648fda0d` 表明这是**有意隔离**。→ 故 **SP-3 的真任务是"重建原生 UI"（新增 F0 前置），非"加开关"**。
2. **后端 `/api/semantic`（SemanticModelingResource）成熟**（34 端点：subjects/objects/dims/metrics/models/bindings/review/runs/publish/register），可直接支撑原生 UI。
3. **dbt `meta.semantic_type` 已存在**（`services/dts-dbt/models/dws/semantic/schema.yml`）→ SP-2 是"贯通已有 meta 到 catalog 列族"，非发明。
4. **SP-2 前置 spike (F2-T00)**：OM dbt ingestion 是否已抓 `meta` → 决定 SP-2 走 OM 镜像还是直读 dbt。
5. **F2(SP-3) 复用平台已有 React Flow 画布**（`analytics/pages/semantic/SemanticModelCanvas`，Chrome95 兼容已处理），不再造。

### 推荐执行顺序（依赖）
1. **SP-2 (Sprint-43)** 先做 F2-T00 spike → 后端贯通列族（解锁 SP-3 F2 字段角色 + SP-1 F3 源表 gating）。**可与 SP-3 F0/F1 并行**（SP-3 F0/F1 不依赖列族）。
2. **SP-3 (Sprint-44)**：**F0 重建原生页（前置）** → F1 治理呈现 → F2 工作台（依赖 SP-2 列族）→ F3 路由收敛。
3. **SP-4 (Sprint-47)**：gated 在 SP-2/SP-3 平价后，灰度退役 dts-metrics。

### 验证命令
```bash
# 后端（SP-1 已绿；SP-2/SP-4 平台改动）
cd source && ./mvnw -pl dts-platform clean test -Dtest='ControlledMetricDslCompilerTest,EltLayerGateTest,SemanticModelingServiceTest,SemanticModelingResourceTest'
# 前端（SP-3）
cd source/dts-platform-webapp && pnpm build   # tsc + vite（Chrome95 LEGACY_BROWSER_BUILD）
```

### 分支/工作树
- 工作基线 = `v2.2.3`（易变，并行 session 频繁切换/merge——见记忆 `dts-metrics-branch-baseline`）。
- SP-1 已 merge v2.2.3。SP-2/3/4 编码建议续 `feat/semantic-consolidation` 或新特性分支；提交时**精确暂存、隔离并行改动**（sprint-42/45/46 + webapp workbench）。
- 铁律：改完 `clean` 再信测试（增量假绿）；record 加字段必同步全部构造点。
