# 跨 Sprint owner 边界

**定稿日期**：2026-08-17
**目的**：Sprint-93（2026-08-17～09-04）与 Sprint-94（2026-08-17～09-18）时间盒完全重叠，且都触及 Catalog 资产身份与 Analytics 访问控制。本表把物理资产 observation、逻辑 BI 数据集身份和授权接缝分开，避免误用物理注册命令或长出第二条授权路径。

## 1. 与 Sprint-93 的接缝

| 关注点 | owner | Sprint-94 的行为 | 禁止 |
|---|---|---|---|
| 稳定资产身份 | Sprint-86/87 定义的 `CatalogAssetType + CatalogAssetKey` | 复用；`QueryDatasetAsset` 使用 `BI_DATASET + CatalogAssetKey.biDataset(id)` | 新建资产身份体系或用名称猜测关联 |
| 物理资产 observation | **Sprint-93 已定：`CatalogAssetRegistrationService` 是稳定物理 DATASET 的唯一 command boundary** | Sprint-94 不修改、不绕过；QueryDataset 不是物理 relation，不调用 `observe(BI_DATASET)` | 扩宽物理 admission 以接收逻辑资产、直写 semantic store |
| BI_DATASET 逻辑事实源 | `QueryDatasetAsset + QueryDatasetVersion` | 直接作为逻辑资产 owner；`CatalogAssetMappingReportService` 已从 QueryDataset 生成 BI_DATASET identity，权限解析器已识别该 key | 为 BI_DATASET 另建注册表、repository 或生命周期 |
| 语义投影 | Sprint-93 F1/F2 | 只读消费，不写 | 在 Analytics 侧生成第二份语义投影 |
| 血缘 | Sprint-90 | 只登记调用事实 | 新建血缘台账 |
| 统一发布 | Sprint-91 | 复用发布语义 | 第二套发布控制面 |
| Analytics 访问控制 | `AnalyticsAssetAccessRegistrar` + `PlatformPermissionFilter`（2026-08-17 `af19ed44e` 落地） | F3/T02 在其上扩展默认拒绝，**不重写** | 绕开 filter 自建鉴权 |

**当前代码证据**：

- `CatalogAssetRegistrationService.java:13` 明确限定为 stable physical-asset observation。
- `CatalogAssetSemanticsContract.java:200-223` 只接收 `CatalogAssetType.DATASET` 与 TABLE/VIEW/MATERIALIZED_VIEW，`BI_DATASET` 会返回 `PHYSICAL_ASSET_TYPE_REQUIRED`。
- `CatalogAssetMappingReportService.java:58-65` 已从 `QueryDatasetAsset` 投影 `BI_DATASET + CatalogAssetKey.biDataset(...)`。
- `CatalogAssetTagPermissionIdentityResolver.java:218-220,638-641` 已支持 BI_DATASET 身份解析。
- `QueryDatasetService.java:240-248` 与 `BiReportLinkService.java:385-399` 已用同一 key 传播密级。

**冲突处理**：若实施期确实需要把 BI_DATASET 写入 Catalog semantic store，先停止编码并新增独立 ADR，明确逻辑资产 command seam、迁移和调用方；不得直接扩宽物理 admission。若同一 owner 被两个 Sprint 同时修改，先在本表登记并裁定归属。

## 2. 可引用但必须复验的 Sprint-93 交付基线

下列事实可用于准备探针与账号，不可直接替代 Sprint-94 当前运行实例的 P2/P5 复验：

| 事实 | 证据 | Sprint-94 用法 |
|---|---|---|
| 真实登录路径曾可用 | `sprint-93/it/baseline.md` P2：2026-08-16 以 `xiezm` 从真实登录页进入受保护页，`/api/session/status`=200、`authenticated=true`、角色含 `ROLE_INST_DATA_OWNER` | F0/T02 复用步骤和账号完成 Sprint-94 当前会话复验；复验前 G0 保持 GAP |
| 受保护 API harness 曾可用 | 同上 P5：登录会话内多个 `/api/**` 返回 200 | F0/T02 复用请求清单重新执行，不复用 PASS 结论 |
| 现代 Chrome 菜单走查 | 同上 P6：Chrome 150 干净重载 console=0 error | 仅作辅助诊断，**不替代** Chrome 95 |

**仍需 Sprint-94 自行取得**：当前会话登录/API 复验、本地 BI 基线样本、A1～A4 四角色的职责分离与部门/角色/密级绑定、Chrome 95、目标环境容量与调用观测画像。

## 3. Sprint-92 上游范围

Sprint-92 已升级为来源无关的统一模型创作。Sprint-94 不占用、不修改 authoring draft、projection、PUBLISHED fork 或旧建模 API 兼容路径；它只消费 Sprint-92/93 发布后形成的稳定、已治理数据集身份。
