# Asset Identity Evidence

**Sprint**: 31A -> 31B -> 32 final IT 共用
**Owner**: Platform catalog owner
**触发命令**: Sprint-32 final IT 中执行资产身份 dry-run、assets-v2 读取和 code asset writer focused tests
**预期输出**: asset_type / asset_key / asset_id 映射一致，自动创建资产生命周期状态明确
**目前状态**: PENDING，待 Sprint-32 final IT 写入实际日志

## Linked Scope

- Sprint-31A F1: `worklog/v2.2.3/sprint-31a-202605/features/F1-asset-identity-lifecycle/README.md`
- Sprint-31B F1: `worklog/v2.2.3/sprint-31b-202605/features/F1-rx-runtime-closure/README.md`

## Evidence To Capture

- assets-v2 列表和详情返回稳定 `assetId`。
- DATASET / DBT_MODEL / SCREEN / METRIC / API_SERVICE / GLOSSARY_TERM / DATA_STANDARD 映射到同一资产身份契约。
- resolver failure 报告为空或每条失败都有 owner 和 remediation。
