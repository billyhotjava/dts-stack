# Sprint-23: OpenMetadata 主目录与 DTS 治理扩展资产门户

**时间**: 2026-05  
**状态**: IN_PROGRESS（主功能已补齐，剩现场截图/发布证据归档）
**类型**: Implementation（OpenMetadata 技术资产主目录 + DTS 治理扩展层 + 统一血缘身份）
**目标**: 将数据资产门户从“DTS 弱 catalog 主导、OpenMetadata 旁路预览”升级为“OpenMetadata 技术资产主数据 + DTS 治理扩展与权限控制”的统一资产目录。

## 背景

Sprint-19 已经打通 OpenMetadata 采集、读路径、质量和血缘的基础闭环，但当前产品形态仍存在明显割裂：

- 数据资产列表主要读取 DTS 本地 `catalog_dataset`，技术元数据能力弱。
- 元数据采集页能看到 OpenMetadata 或本地回退，但没有成为资产门户主体验。
- 资产详情页使用 `catalog:<datasetId>` 强制走本地 catalog，绕开了 OpenMetadata。
- 质量报告和血缘存在 OpenMetadata 兜底能力，但不是统一资产身份下的自然能力。
- DTS 自定义治理属性（密级、部门、权限、生命周期、审批、脱敏）仍必须保留，不能简单把资产页直接切到 OpenMetadata UI/API。

本 Sprint 采用方案 B：

```text
OpenMetadata = 技术资产主目录
DTS = 治理扩展层 + 权限/审计/业务血缘覆盖层
```

## 产品原则

1. **技术事实以 OpenMetadata 为准**：表、字段、schema、database service、FQN、profile、test case、技术血缘优先来自 OpenMetadata。
2. **治理属性由 DTS 承载**：密级、归属部门、生命周期、权限申请、脱敏、行过滤、指标绑定、数据产品、审批和审计继续在 DTS。
3. **本地 cache 隔离运行依赖**：页面不直接依赖实时 OpenMetadata API；后台同步到 DTS cache，再由 DTS 聚合接口服务前端。
4. **统一资产身份**：技术资产使用 `om_entity_id` / `fqn`，DTS 旧 `catalog_dataset.id` 作为兼容映射和治理扩展引用。
5. **血缘从一开始纳入方案**：OpenMetadata 提供 table/column/dbt 技术血缘，DTS 叠加接入任务、运行批次、指标、报表、权限影响等业务节点。
6. **未治理资产也要可见**：OpenMetadata 中存在但 DTS 未定级/未归域/未认领的资产进入“待治理”状态。
7. **不双写同一事实**：避免 DTS 和 OpenMetadata 各自维护一套 table/column 技术事实；DTS 只保存 cache、扩展和覆盖层。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|--------:|--------|------|
| F1 | OpenMetadata 资产缓存与同步模型 | 5 | P0 | DONE |
| F2 | DTS 治理扩展层 | 5 | P0 | DONE |
| F3 | 资产映射、迁移与兼容 | 5 | P0 | DONE |
| F4 | 资产聚合 API 与权限过滤 | 5 | P0 | DONE |
| F5 | 数据资产门户重构 | 5 | P0 | DONE |
| F6 | 质量、血缘与技术详情融合 | 6 | P0 | DONE |
| F7 | 验收、发布与回滚 | 5 | P0 | IN_PROGRESS |

**合计 36 个 task。**

## 范围边界

### 本 Sprint 做

- 新增 OpenMetadata table/column/lineage/quality cache 模型。
- 新增 DTS asset extension，承载密级、部门、生命周期、权限和治理状态。
- 建立 `om_entity_id` / `fqn` / `dataset_id` 映射。
- 将数据资产列表改为 OpenMetadata cache 主导，DTS extension 左连接。
- 资产详情页展示 OpenMetadata 技术详情，同时保留 DTS 治理属性。
- 统一质量、血缘、技术元数据详情的身份解析。
- 支持待治理资产的认领、定级、归域入口。
- 保留旧 `catalog_dataset` API 的兼容路径和迁移策略。

### 本 Sprint 不做

- 不把用户直接跳转到 OpenMetadata UI 作为主要资产体验。
- 不把权限审批、脱敏、行过滤迁移到 OpenMetadata。
- 不强行把 owner/domain/tags 双写到 OpenMetadata；如需写入，单独定义后续策略。
- 不重写 OpenMetadata ingestion 引擎；复用 Sprint-19 的采集与脚本。
- 不替换 Sprint-20 的业务血缘可视化，只升级其资产身份和 OM 技术血缘输入。

## 目标架构

```text
                     OpenMetadata
        table / column / profile / tests / lineage
                          │
                  scheduled sync / webhook
                          ▼
                 DTS OpenMetadata Cache
     om_asset_cache / om_column_cache / om_lineage_cache
                          │
                          ├──────────────┐
                          ▼              ▼
              DTS Asset Extension   DTS Lineage Overlay
       classification / ownerDept   task / run / metric / BI
       lifecycle / grants / masks   governance impact nodes
                          │              │
                          └──────┬───────┘
                                 ▼
                         DTS Asset Graph API
                                 │
                                 ▼
                         数据资产门户 / 血缘 / 质量
```

## 数据模型方向

```text
om_asset_cache
  id, om_entity_id, fqn, service, database_name, schema_name, table_name,
  display_name, description, owner_name, domain_name, tags_json,
  column_count, profile_json, raw_json, last_synced_at, sync_status

om_column_cache
  id, asset_id, om_column_fqn, name, data_type, description,
  ordinal_position, tags_json, profile_json, raw_json

catalog_asset_extension
  id, om_asset_id, legacy_dataset_id, classification, owner_dept,
  business_owner, lifecycle_status, domain_id, enabled,
  governance_status, security_policy_refs, created_by, updated_by

catalog_asset_mapping
  id, om_entity_id, fqn, legacy_dataset_id, source_id,
  match_status, match_reason, confidence, last_checked_at

om_lineage_cache
  id, from_om_entity_id, to_om_entity_id, from_fqn, to_fqn,
  edge_type, source, raw_json, last_synced_at
```

## 依赖关系

```text
Sprint-19 OpenMetadata 采集闭环
        │
        ├──> F1 OM cache 模型 ──┐
        │                       ├──> F3 映射迁移 ──> F4 聚合 API ──> F5 资产门户
Sprint-21 Connector Center ─────┘                         │
                                                           ▼
Sprint-20 Lineage Graph ───────────────────────────────> F6 血缘/质量融合
                                                           │
                                                           ▼
                                                      F7 验收发布
```

## 完成标准

- [x] OpenMetadata 中存在的表能进入 DTS 数据资产列表，即使尚未完成 DTS 治理扩展。
- [x] 资产列表可区分 `已治理`、`待认领`、`待定级`、`OpenMetadata 未同步`、`本地遗留` 状态。
- [x] 资产详情技术信息来自 OM cache，治理属性来自 DTS extension。
- [x] 旧 `catalog_dataset` 数据完成映射，存量密级/部门/生命周期不丢失。
- [x] 质量、血缘、字段详情使用同一资产身份解析，不再各自拼 FQN。
- [x] 血缘图能展示 OpenMetadata 技术血缘和 DTS 业务/运行节点，并标注来源。
- [x] OpenMetadata 不可用时，已同步 cache 仍可支撑资产列表和详情。
- [x] 回滚后旧资产列表、旧详情和本地治理属性仍可读取。

## 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| FQN 规则不一致 | 资产映射和血缘命中失败 | F3 先做映射诊断和置信度，不静默合并 |
| OpenMetadata 数据历史污染 | 错误资产进入门户 | F1 引入同步过滤和 forbidden database 策略 |
| DTS 权限模型无法直接套到 OM 资产 | 数据泄露风险 | F4 所有聚合 API 统一走 DTS access checker |
| cache 与 OpenMetadata 延迟 | 页面展示非实时 | 标注 lastSyncedAt，提供手动刷新 |
| 旧 `catalog_dataset` 语义混杂 | 迁移复杂 | 保留 legacy mapping，分阶段迁移 |
| 血缘双源冲突 | 图重复或方向错误 | F6 边来源和去重规则显式化 |

## 关键代码触点

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/openmetadata/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/`
- `source/dts-platform/src/main/resources/config/liquibase/changelog/`
- `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx`
- `source/dts-platform-webapp/src/pages/catalog/AssetDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx`
- `source/dts-platform-webapp/src/pages/catalog/QualityPage.tsx`
- `services/dts-openmetadata/ingestion/`

## 相关 Sprint

- `worklog/v2.2.3/sprint-19-202604/README.md` — OpenMetadata 元数据采集闭环。
- `worklog/v2.2.3/sprint-20-202604/README.md` — Data Lineage 端到端可视化。
- `worklog/v2.2.3/sprint-21-202604/README.md` — Connector Center 工业级数据接入中心。
- `worklog/v2.2.3/sprint-22-202605/README.md` — API 数据接入运行时与工业级验收。

## 当前落地切片

- 2026-04-30：启动 F1/F2/F3/F4 后端基础切片，新增 `om_asset_cache`、`om_column_cache`、`catalog_asset_extension`、`catalog_asset_mapping`、`om_lineage_cache` changelog；新增 JPA 实体和 repository；新增 OpenMetadata table/column cache 同步服务、自动 legacy dataset 映射和 governance extension 初始化；新增 `/api/catalog/assets-v2` 聚合列表/详情和 `/sync` 手动同步入口；前端 API client 预留 `listCatalogAssetsV2`、`getCatalogAssetV2`、`syncCatalogAssetsV2`。
- 2026-05-01：启动 F5 前端贯通切片，数据资产地图改读 `/api/catalog/assets-v2`，筛选参数透传到聚合 API，新增“同步OpenMetadata”操作；`/catalog/datasets/:id` 详情页保留旧 `catalog_dataset` 优先路径，失败时回退到 OpenMetadata cache 详情，未映射资产可查看 OM 字段，已映射资产继续进入 DTS 血缘和治理健康。
- 2026-05-01：补齐治理扩展更新、映射诊断、OM lineage cache 同步与查询、资产门户 v2 回退开关、前端待治理/映射异常筛选和 smoke 脚本；`VITE_CATALOG_ASSET_PORTAL_V2=false` 可回退旧 `catalog_dataset` 资产列表主路径。
- 2026-05-01：补齐资产详情技术页（profile/raw metadata）、OM 资产治理扩展编辑入口和 `securityPolicyRefs` 聚合 DTO；新增发布/回滚 Runbook。`pnpm build` 与 `dts-platform ./mvnw -q -DskipTests compile` 通过。

## 剩余收口项

- F7 仍需在目标环境归档浏览器截图、SQL 映射统计和发布记录。
- `lineage-quality-smoke.sh` 需要带现场 `DTS_OM_ASSET_ID` / `DTS_LEGACY_DATASET_ID` 执行，归档到 `it/evidence/<date>-<env>/`。
