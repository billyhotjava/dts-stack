# Permission Classification Evidence

**Sprint**: 31A -> 31B -> 32 final IT 共用
**Owner**: Platform security owner
**触发命令**: Sprint-32 final IT 中执行 asset permission check、policy endpoint、RLS / masking focused tests
**预期输出**: asset_grant、密级、RLS predicate、column masking 和拒绝原因都可审计
**目前状态**: PENDING，待 Sprint-32 final IT 写入实际日志

## Linked Scope

- Sprint-31A F4: `worklog/v2.2.3/sprint-31a-202605/features/F4-permission-classification/README.md`
- Sprint-31B F2: `worklog/v2.2.3/sprint-31b-202605/features/F2-rls-publish-and-masking-closure/README.md`
- Sprint-31B F4/T02: `worklog/v2.2.3/sprint-31b-202605/features/F4-sprint-31a-gap-and-status-rectification/T02-permission-audit-denial-reason-uplift.md`

## Evidence To Capture

- 有授权用户可读资产，无授权用户返回结构化拒绝。
- policy endpoint 返回 row-filter predicates 和 masked columns。
- dts-metrics preview / publish 不绕开 platform policy。
- 拒绝原因具备 reason code、reason detail 和 suggested remediation。
