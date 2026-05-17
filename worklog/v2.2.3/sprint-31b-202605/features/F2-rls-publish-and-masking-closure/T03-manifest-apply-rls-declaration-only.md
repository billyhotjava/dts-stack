# T03: manifest `apply_rls=true` 降级为声明

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把 metric-pack manifest 里的 `security.apply_rls=true` 从「视为已生效」改成「仅声明意图，实际由 platform policy 决定」。同时引入显式失败模式：声明 `apply_rls=true` 但 platform 没有返回任何 predicate / masked column 时，强制 preview 失败。

## 背景

Sprint-31A RX/T05 当前的 `applyRls(manifest)` 检查只决定要不要调 policy contract；如果 platform 返回空 policy，preview 仍照常出 artifact，等于 manifest 自己声明 `apply_rls=true` 就被信任了。这是 self-attestation 反模式。

## 技术设计

1. 在 `MetricArtifactGenerationService.preview(...)` 中：
   - 若 manifest 声明 `apply_rls=true` 而 platform `RlsPolicyResult.applyRls=false` 或 `predicates.isEmpty() && maskedColumns.isEmpty()` → 直接 fail：
     ```
     errors.add("metric-pack declares apply_rls=true but platform policy returned empty; ask platform admin to configure row-filter or column-mask for asset");
     ```
   - 若 manifest 声明 `apply_rls=false` 而 platform 返回非空 policy → 强制覆盖 manifest，按 platform 策略注入并 warn。
2. metric-pack validation 把 `security.apply_rls` 标注为 declaration only：
   ```yaml
   security:
     apply_rls: true   # declaration only; platform enforces final policy
   ```
3. 更新 metric-pack 文档：「`apply_rls` 是声明，不是开关」。

## 影响范围

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/validation/MetricPackValidationService.java`
- `source/dts-metrics/src/main/resources/docs/metric-pack-spec.md`（如存在）

## 验证

- [ ] `MetricArtifactGenerationServiceTest.declaresApplyRlsButPlatformEmpty_failsPreview`
- [ ] `MetricArtifactGenerationServiceTest.declaresApplyRlsFalseButPlatformHasPolicy_overrides`
- [ ] `MetricArtifactGenerationServiceTest.platformPolicyTrustedOverManifest`

## 完成标准

- [ ] manifest `apply_rls` 不再被独立 trust，仅作声明。
- [ ] 声明与 platform policy 冲突时显式 fail / override 并告警。
- [ ] 文档更新。
