# T02: F4/T05 拒绝原因提级到 P0

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

把 Sprint-31A F4/T05「权限审计和拒绝原因」从 P1 提级到 P0，并落地结构化拒绝原因 schema —— 合规审计必须能回答「谁、什么时候、为什么被拒绝访问什么资产」。

## 背景

Sprint-31A F4/T05 标记 P1，但在企业级数据平台里这是 mandatory compliance 要求：
- 数据安全合规要求 6 个月审计日志保留
- 拒绝原因必须结构化（不能只是字符串）以支持 SIEM 抽取
- 必须有「reason code + reason detail + suggested remediation」三段式

当前 `PermissionDecision.reason()` 只是单一字符串字段，无法满足合规。

## 技术设计

1. `PermissionDecision` 扩展为：
   ```java
   public record PermissionDecision(
       boolean allowed,
       String reasonCode,           // ENUM: NO_GRANT / CLASSIFICATION_MISMATCH / DEPT_MISMATCH / LIFECYCLE_BLOCKED / ROLE_MISSING
       String reasonDetail,         // 人可读细节
       String suggestedRemediation, // "申请 grant" / "升级 user classification" 等
       Instant deniedAt
   ) {}
   ```
2. `AssetPermissionService.checkAction(...)` 内的所有 deny 路径必须填充 `reasonCode`。
3. `AssetPermissionAuditService` 同步存 `reason_code` 列（structured）；保留 `reason_detail` 给运维。
4. 增加 `/api/internal/v1/asset-permission/audit/denied?since=&reasonCode=&assetId=` 内部查询接口。
5. 合规审计可导出 CSV：`actor, assetType, assetId, reasonCode, reasonDetail, deniedAt`。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/permission/AssetPermissionService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/permission/AssetPermissionAuditService.java`
- 新增 changelog（`asset_permission_audit.reason_code` 列）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionAuditQueryResource.java`

## 验证

- [ ] `AssetPermissionServiceTest.deny_setsReasonCode` 覆盖 5 个 reasonCode
- [ ] `AssetPermissionAuditServiceTest.persistsStructuredReason`
- [ ] CSV 导出 schema review

## 完成标准

- [ ] `PermissionDecision` 结构化 reason。
- [ ] audit 表持久化 reasonCode。
- [ ] 合规导出可用。
