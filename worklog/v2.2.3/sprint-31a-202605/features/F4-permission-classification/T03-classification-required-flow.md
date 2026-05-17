# T03: 密级写入和缺失处理

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

把 classification 作为资产治理和权限控制字段，处理历史缺失资产和新资产写入。

## 技术设计

- 新资产缺 classification 时由治理契约进入 `PENDING_GOVERNANCE` / `PENDING_CLASSIFICATION`。
- 历史缺失资产通过治理缺口展示，不默认公开。
- `AccessChecker.canRead` 将缺失 classification 判为拒绝，超级管理员保留治理查看能力。
- 内部权限契约返回 `classification_required` 和 `MISSING_ASSET_CLASSIFICATION`。

## 影响范围

- Catalog entity
- Asset portal governance panel
- screen/report/dataset publish paths

## 验证

- [x] `classification=null` 不等价于 public。
- [x] 缺密级资产在内部权限契约中明确返回 `classification_required`。
- [x] 变更密级有审计事件。

## 完成标准

- [x] 密级控制从 UI 字段升级为治理事实。

## 交付物

- `AccessChecker.canRead` 缺密级拒绝
- `AssetPermissionService.checkAction` 缺密级拒绝原因
- `CatalogAssetPortalResource.updateGovernance` 治理字段审计 payload
- `worklog/v2.2.3/sprint-31a-202605/assets/classification-required-flow.md`
