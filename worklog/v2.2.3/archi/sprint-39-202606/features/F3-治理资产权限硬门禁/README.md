# F3: 治理资产权限硬门禁

**优先级**: P0
**状态**: DONE

## 目标

把治理、资产、血缘和权限从“可填写信息”升级为发布和消费前的硬门禁，避免未治理数据进入报表或服务。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 资产身份统一与重复资产收口 | P0 | DONE | F1-T02 |
| T02 | owner 分级标准质量必填门禁 | P0 | DONE | T01 |
| T03 | 血缘失败待治理状态 | P0 | DONE | T02 |
| T04 | 权限审批与 RLS 消费一致性 | P0 | DONE | T03 |

## 完成标准

- [x] 发布资产必须有唯一资产身份、owner、分级分类、质量和血缘证据。
- [x] 权限、RLS、masking 的消费结果在资产门户、metrics、BI、大屏、API 服务一致。

## 进度记录

- 2026-06-14: T01 已完成。新增 `GoldenChainAssetIdentityResolver`，覆盖唯一资产身份、无法解析诊断和重复资产待治理；验证：`./mvnw -q -Dtest=GoldenChainAssetIdentityResolverTest test`。
- 2026-06-14: T02 已完成。新增 `GoldenChainGovernanceGateService`，覆盖 owner、分级分类、质量规则/结果、DWD 主键/标准码、DWS/ADS 粒度/指标口径门禁；验证：`./mvnw -q -Dtest=GoldenChainGovernanceGateServiceTest test`。
- 2026-06-14: T03 已完成。新增 `GoldenChainLineageGovernanceService`，覆盖 OpenLineage/dbt manifest/Addax 血缘诊断、缺 source/target 和缺 evidenceRef 待治理；验证：`./mvnw -q -Dtest=GoldenChainLineageGovernanceServiceTest test`。
- 2026-06-14: T04 已完成。新增 `GoldenChainPermissionConsistencyService`，覆盖 platform policy/RLS/masking hash 一致性、legacy fallback 阻断和无权限安全提示；验证：`./mvnw -q -Dtest=GoldenChainPermissionConsistencyServiceTest test`。
