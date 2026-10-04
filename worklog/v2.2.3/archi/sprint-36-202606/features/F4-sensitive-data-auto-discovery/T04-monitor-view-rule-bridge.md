# T04: 敏感数据监控查询视图 + 建议转脱敏规则

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标

提供敏感数据监控查询视图（命中字段、规则、密级、是否已配脱敏），并支持扫描建议一键转 `CatalogMaskingRule`，与 2.1 分类映射、2.3 脱敏链路联动。

## TDD 测试先行（RED）

- 新增 `SensitiveScanMonitorResourceTest`（放 `dts-platform/src/test/java/.../web/rest/security/`）：
  - 监控查询支持按 dataset/classification/`alreadyMasked` 过滤与分页，断言返回命中字段/规则/密级/是否已脱敏。
  - 一键转规则：对某候选调用 `convertToMaskingRule` → 落一条 `CatalogMaskingRule`（column/function/args 来自候选建议），重复转换幂等不重复建规则。
  - `alreadyMasked=true` 的候选不可再转（返回明确业务码而非 500）。
- 前端 source 契约测试（`dts-platform-webapp/test/**` 或既有 source-contract 测试）：断言监控页调用的 API path 与列字段一致。

## 技术设计（GREEN）

- 新增 `web/rest/security/SensitiveScanMonitorResource.java`：监控查询（过滤/分页）+ `convertToMaskingRule` 端点。
- 转规则复用 `service/security/CatalogMaskingService.java` 与 `domain/catalog/CatalogMaskingRule.java`（column/function/args），函数取值对齐 `service/security/MaskingFunctions.java`；转换后回写候选 `alreadyMasked`。
- 与分类映射联动：候选 `classification` 对齐 `domain/catalog/CatalogClassificationMapping.java`，并刷新 `CatalogMaskingResource.validateClassificationMappingInternal`（约 line 194）的 `MASKING_GAP` 判定，使扫描命中后 gap 收敛。
- 前端在 `dts-platform-webapp/src/pages/security/data-security.tsx` 新增「敏感数据监控」Tab，复用既有「脱敏规则」表单做一键转规则。
- 改既有 `CatalogMaskingResource` / `CatalogMaskingService` 前先 `gitnexus_impact`；`Optional` 用 `orElseThrow()`。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/security/SensitiveScanMonitorResource.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/CatalogMaskingService.java`（改既有，先 `gitnexus_impact`）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogMaskingResource.java`（`MASKING_GAP` 判定联动，改既有先 `gitnexus_impact`）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogMaskingRule.java`、`CatalogClassificationMapping.java`（复用）
- `source/dts-platform-webapp/src/pages/security/data-security.tsx`（新增监控 Tab）

## 验证

- [ ] 监控查询可按 dataset/密级/是否已脱敏过滤分页，列字段齐全。
- [ ] 候选一键转 `CatalogMaskingRule` 成功且幂等。
- [ ] 已脱敏候选不可重复转，返回明确业务码。
- [ ] 扫描命中后 `MASKING_GAP` 判定相应收敛。

## 完成标准

- [ ] 监控视图 + 建议转规则闭环可用，与分类映射/脱敏链路联动。
