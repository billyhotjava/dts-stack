# T05: Metric artifact preview / RLS enforcement

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01, T02

## 目标

把 metric-pack 的 `security.apply_rls=true` 从 manifest 声明推进到运行时 enforcement：预览、导入、发布和生成 SQL 都必须通过 platform 统一权限与安全策略。

## 已完成

- [x] `source_model` 必须绑定到 `dependencies.platform_assets` 中的可授权资产，不能直接绕过 platform asset。
- [x] inline `term_ids` 必须在 `dependencies.platform_assets` 中声明为 `GLOSSARY_TERM`。
- [x] artifact preview / import 前通过 platform internal API 校验 glossary term 存在且处于 `ACTIVE`。
- [x] glossary resolver 对别名冲突返回 `ambiguous`，不依赖 repository 返回顺序。
- [x] dts-metrics client 对 glossary resolve 自动按 200 条分批，避免行业包撞平台端批量上限。
- [x] platform contract 不可达时返回可读预览错误，不向界面透出底层 RestClient 异常。
- [x] artifact preview 阶段调用 `/api/internal/asset-permission/check`，无权访问 source asset 时拒绝生成，错误不暴露资产名称。
- [x] metrics internal service principal 通过 `dts.metrics.service-name` 配置判断，不再在 endpoint SpEL 中硬编码。

## 待完成范围

- [ ] SQL 生成器根据 platform policy 注入 RLS predicate 或 security view。
- [ ] publish gate 复用同一 RLS / masking 策略，禁止 preview 与 publish 口径不同。
- [ ] manifest 的 `security.apply_rls=true` 降级为声明，不再被视作已经生效。
- [ ] live IT 覆盖无授权 asset 无法 preview/publish，且错误不泄露资产详情。

## 验收建议

- `MetricArtifactGenerationServiceTest` 覆盖无授权 preview、platform outage、glossary ambiguous、source model suffix 边界。
- `PlatformContractClientTest` 覆盖 glossary batch split 和 transport exception wrap。
- 后续补充 SQL RLS golden file，验证生成 SQL 包含 platform policy 输出的 predicate。
