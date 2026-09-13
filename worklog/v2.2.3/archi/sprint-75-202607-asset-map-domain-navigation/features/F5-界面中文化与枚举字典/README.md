# F5：界面中文化与枚举字典

**优先级**：P0
**状态**：DRAFT

## 目标

界面上不出现英文枚举原值。做法不是逐处补文案，而是建立一个**统一枚举字典 + 可测的漏译降级策略**，并用契约测试把字典钉在后端枚举全集上，防止后续新增枚举时再次漏译。

> **执行顺序**：本 Feature 的 T01（字典模块）是 F3、F4 的前置——新组件必须直接用字典，不得再写第二套映射。编号靠后不代表实施靠后。

## 1. 现状：漏译是系统性的

所有枚举都有中文映射表，但**全部用 `|| 原值` 兜底**，未收录的枚举一律漏英文，且漏了没人知道。

| # | 位置 | 漏出内容 |
|---|---|---|
| C01 | `assetPageShared.tsx:79-84` | `ODS / STG / DWD / DIM / DWS / ADS` 直接当 label（仅 SOURCE、OTHER 已翻） |
| C02 | `AssetLedgerView.tsx:166` | `<Tag>{row.type}</Tag>` 显示 `HIVE`/`JDBC`/`FILE`；而筛选下拉是中文 → 同页自相矛盾 |
| C03 | `AssetLedgerView.tsx:182` | readiness 无原因时兜底显示 `row.governanceStatus` 原值 |
| C04 | `AssetOverviewPage.tsx:334` | `GOVERNANCE_STATUS_LABELS[status] \|\| status` |
| C05 | `assetPageShared.tsx:156` | `classificationText` 的 `\|\| normalized` 兜底 |
| C06 | `DatasetsPage.tsx:509` | 导出 CSV 写 `row.type` 原值 |

## 2. 枚举全集与覆盖缺口

### 2.1 治理状态（后端实有 8 个，前端字典只收 5 个）

| 枚举 | 中文 | 现状 |
|---|---|---|
| `GOVERNED` | 已治理 | 已收录 |
| `PENDING_CLAIM` | 待认领 | 已收录 |
| `PENDING_CLASSIFICATION` | 待定级 | 已收录 |
| `PENDING_DOMAIN` | 待归域 | 已收录 |
| `DISABLED` | 已停用 | 已收录 |
| `PENDING_GOVERNANCE` | 待治理 | **缺失** → 必漏英文 |
| `PENDING_APPROVAL` | 待审批 | **缺失** |
| `PENDING_LINEAGE` | 待补血缘 | **缺失** |
| `PENDING_REVIEW` | 待复核 | **缺失** |

`PENDING_GOVERNANCE` 已被 `assetPortalUx.helpers.ts:52` 实际使用，是当前必然复现的漏译。

### 2.2 生命周期（`CatalogAssetLifecycleStatus`，9 个，前端零收录）

| 枚举 | 中文 |
|---|---|
| `DISCOVERED` | 已发现 |
| `PENDING_GOVERNANCE` | 待治理 |
| `DRAFT_GOVERNANCE` | 治理草稿 |
| `TESTING` | 测试中 |
| `ACTIVE` | 生效 |
| `DEPRECATED` | 已弃用 |
| `ARCHIVED` | 已归档 |
| `BLOCKED` | 已阻断 |
| `PENDING_REVIEW` | 待复核 |

### 2.3 数仓分层（中文主 + 代号弱化，ADR-75-13）

| 枚举 | 中文 | 代号 |
|---|---|---|
| `SOURCE` | 来源层 | SOURCE |
| `ODS` | 贴源层 | ODS |
| `STG` | 暂存层 | STG |
| `DWD` | 明细层 | DWD |
| `DIM` | 维度层 | DIM |
| `DWS` | 汇总层 | DWS |
| `ADS` | 应用层 | ADS |
| `OTHER` | 未分层 | —（不显示代号） |

代号以小号次要色跟在中文后。保留代号是因为本项目有 Addax→dbt→Airflow 链路，`dwd_xxx` 这类模型名需要可对上。

### 2.4 资产类型

`HIVE`/`JDBC`/`FILE` 不新增翻译，改为**复用既有 `TYPE_OPTIONS` 的 label**（Hive / JDBC / 文件）。Hive、JDBC 是产品名与技术标准名，按项目语言规范保持原形。C02 的实质是"表格没用上已有字典"，不是缺翻译。

## 3. 契约定义

| 类型 | 契约 | 关键签名 |
|---|---|---|
| 字典模块 | **新建** `pages/catalog/assets/assetEnumLabels.ts` | 不塞进 `assetPageShared.tsx`（该文件已近 200 行，按项目"多个小文件优先"规范另建） |
| 解析函数 | `resolveEnumLabel(dict, value, fallback?)` | 未收录时返回 `未知（<原值>）`，**不返回裸原值** |
| 分层 | `LAYER_META[k].label` 改中文，新增 `code` 字段 | `label: "明细层", code: "DWD"` |
| 渲染 | `<LayerLabel layer={...} />` | 统一中文主 + 代号弱化的呈现，避免各处手拼 |
| 导出 | CSV 表头与单元格 | 全部走字典（C06） |

### 漏译降级策略

未收录枚举显示 `未知（PENDING_XXX）` 而不是裸 `PENDING_XXX`。理由：漏译因此在界面上**可见且可测**——契约测试可断言正常数据下不出现"未知（"，避免漏译静默存活。

## 4. 防漂移门禁

沿用本项目已有的"前端测试直读后端源文件"模式（`DataAssetPortalMenu.source-contract.test.ts` 直读 `portal-menu-seed.json`）：

契约测试解析 `CatalogAssetLifecycleStatus.java` 的枚举常量，断言前端字典**逐个覆盖**。后端新增枚举而前端未补译时，测试立即失败。治理状态因散落在字符串常量中，改为断言字典覆盖 F5/2.1 表列出的 8 个值，并在表旁注明来源位置。

## 5. Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立 assetEnumLabels 字典模块与降级策略 | P0 | DRAFT | F0 |
| T02 | 分层改中文主 + 代号弱化并接入 LayerLabel | P0 | DRAFT | T01 |
| T03 | 补齐治理状态与生命周期字典 | P0 | DRAFT | T01 |
| T04 | 消除台账与导出的原值直显 | P0 | DRAFT | T01 |
| T05 | 建立枚举覆盖防漂移契约测试 | P0 | DRAFT | T03 |

## 6. Definition of Ready

- [ ] 已确认 `LAYER_META` 的 `color`/`tone` 消费方，改 label 不影响配色
- [ ] 已确认 `resolveAssetReadiness` 返回的 `label`/`reasons` 已是中文，无需纳入字典
- [ ] 已确认导出 CSV 的既有列头语言，避免半中半英

## 7. Definition of Done

- [ ] G-75-07 通过：正常数据下界面不出现英文枚举原值，也不出现"未知（"
- [ ] G-75-08 通过：字典覆盖 `CatalogAssetLifecycleStatus` 全部 9 个枚举，缺一即测试失败
- [ ] C01～C06 六处漏出点全部消除
- [ ] 分层在地图、台账、详情三处呈现一致（中文主 + 代号弱化）
- [ ] `tsc --noEmit` 与前端 build 通过
