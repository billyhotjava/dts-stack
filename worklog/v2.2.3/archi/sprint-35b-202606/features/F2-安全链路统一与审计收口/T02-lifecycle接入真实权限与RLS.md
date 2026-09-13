# T02: lifecycle 接入真实 permission + RLS 解析

**优先级**: P0
**状态**: DONE（占位符 policySource 已替换为真实解析；无权→403 已测）
**依赖**: T01

## 目标

`MetricModelLifecycleService.generateArtifacts` 用真实 platform 解析替换占位符 policySource/predicateHash，并在生成前校验来源资产权限。

## 技术设计

- generateArtifacts 中，对 graph 的 `base`/源资产调 `MetricSecurityPolicyService.requirePermission`（preview/source 权限），无权 → 403 `asset_permission_denied`。
- 调 `resolvePolicy` 得到真实 `RlsPolicyResult`，其 policySource/predicateHash 写入 securitySnapshot，替换当前 `"platform-policy-required"` + 空哈希。
- 保持与 validate 阶段 predicateHash 一致性校验链（platform 侧已校验外层字段一致）。

## 影响范围
- `MetricModelLifecycleService.generateArtifacts` / securitySnapshot。

## 验证
- [ ] 无权用户 generate → 403。
- [ ] securitySnapshot.policySource/predicateHash 来自真实解析，validate 一致性校验通过。

## 完成标准
- [ ] lifecycle 不再出现占位符 policy。
