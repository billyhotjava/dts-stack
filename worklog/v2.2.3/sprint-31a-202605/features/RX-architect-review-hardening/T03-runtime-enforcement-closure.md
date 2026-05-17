# T03: 运行时 enforcement 闭环

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01, T02

## 目标

把当前已经定义和测试的资产/指标契约接入运行时，避免停留在 `defined` 状态。

## 待完成范围
- [x] `source_model` 必须绑定到 `dependencies.platform_assets` 中的可授权资产，不能直接绕过 platform asset。
- [x] inline `term_ids` 必须在 `dependencies.platform_assets` 中声明为 `GLOSSARY_TERM`，不能仅作为普通字符串出现。
- [x] metric-pack 预览/导入前通过 platform 内部 API 校验 `term_ids` 对应 glossary term 存在且处于 `ACTIVE` 状态。
- [ ] 新增或接入 `CodeAssetGrantWriter` 调用方，把 `GovIndicator*`、`Modeling*`、`DataStandard`、`Glossary`、`SvcApi*` 等代码化资产写入 platform asset 事实源。
- [ ] 扩展 `CatalogAssetIdentityResolver`，支持 `codeAsset`、`metricPack`、`scopedDataset` 解析和 `asset_grant` 校验。
- [ ] `MetricArtifactGenerationService` / publish gate 不能只接受 `security.apply_rls=true` 声明，必须通过 platform policy 生成或注入 RLS 条件。

## 验收建议

- `MetricArtifactGenerationService` 增加 glossary existence / ACTIVE 状态 contract test。
- `MetricArtifactGenerationService` 增加 source model 未绑定 asset 时拒绝生成的测试。
- platform resolver 增加 `GLOSSARY_TERM` / `GOV_INDICATOR` / `METRIC_PACK` identity 测试。
- live IT 中验证无授权 asset 无法 preview/publish。
