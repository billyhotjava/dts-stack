# F1：物理资产登记与语义投影收敛

**优先级**：P0
**状态**：IN_PROGRESS（T01 已完成组件实现与定向测试，等待运行态 IT；T02 等待 F0 的存量 preview）

## 目标

让采集、dbt、模型物化和发布对稳定物理关系的观察统一进入 `CatalogAssetRegistrationService`，并安全补齐存量语义投影，使目录身份、五轴状态、统计与消费资格能够用同一 AssetKey 对账。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 观察 | `POST /api/internal/catalog/asset-observations` | `ObservationCommand[]`，1～500；admitted/excluded/reason/receipt |
| 单条/统计读 | `/api/catalog/assets-v2/semantics`、`/stats-projection` | `AssetSemanticsView`、`StatsSnapshot(asOf,...)` |
| 生产 adapter | ingestion/dbt/materialization → `observe` | assetKey、resourceId、relationType、producer、evidenceRef、statusAxes |
| 存量迁移 | 既有 normalization resource/service 下扩展 semantic-projection preview/apply/rollback | previewHash、batchId、≤500、version drift 409 |
| 数据 | `catalog_dataset` + `catalog_asset_semantic_projection` + stats projection | `(asset_type,asset_key)` 唯一；resourceId 指向唯一 dataset |

## UI/UX 规格

本 Feature 不新增页面。迁移为受控运维接口；用户可见结果通过现有资产概览、目录和详情展示。无法自动解析的资产继续存在并显示“待补充来源证据”，不能消失或被猜测归类。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 接入生产资产观察并统一目录与统计事实 | P0 | IN_PROGRESS | 组件实现已完成；等待 F0 样本与运行态 IT |
| T02 | 分批补齐存量语义投影并可回滚 | P0 | DRAFT | F0/T01、T01 |

## Definition of Ready

- [x] T01 输入、输出、错误和复用 seam 已钉定。
- [ ] T02 的真实 preview 分布和回滚样本已取得。
- [x] 不新增台账、不重写旧 warehouse layer。

## 完成标准

- [ ] 新物化关系自动得到单一目录身份和语义投影。
- [ ] 500+ 数据量下概览与目录可按同一 asOf 对账。
- [ ] 存量迁移可 preview/apply/rollback，歧义 fail-closed。
- [ ] 用户已有业务治理结果不被自动回填覆盖。
