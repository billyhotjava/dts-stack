# T04: 回归验证（PKI/RLS/masking/审计不回归）

**优先级**: P0
**状态**: READY
**依赖**: F1-F5

## 目标

验证 F1-F5 的安全改动未破坏既有能力：PKI/CA + USBKey 登录可用、`DataLevelSqlHelper` 行级密级与 FIELD/masking 策略不回归、审计链路完整。

## TDD 测试先行（RED）

- 先以既有测试为回归基线扩跑，行为漂移即 FAIL：
  - 复跑 `PkiVerificationServiceTest`、`PkiLoginAuditEvidenceTest`（M10 line 68），断言 `verifyPkcs7(...)` 与 signType SM2/RSA/PM 路径仍通过。
  - `UsbKeyBindingRegressionIT`（`com.yuzhi.dts.platform.web.rest`）：`SecurityPkiResource` 的 `GET /api/security/pki/status`、`PUT /bind`、`DELETE /bind` 行为与 certSerial 审计不变。
  - `DataLevelMaskingParityIT`（`...security.policy`）：`DataLevelSqlHelper` 行级密级过滤与现有 `CatalogMaskingRule` FIELD 脱敏在引入 `IamAssetActionPolicy` 后口径一致。
  - `AuditChainIntegrityIT`：关键动作（登录/越权拦截/敏感扫描/台账导出）均产出审计事件，`SEC_BASELINE_AUDIT_LOG` 链路完整。

## 技术设计（GREEN）

- 回归锚点（真实路径）：
  - PKI：`source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/pki/PkiVerificationService.java`、`PkiChallengeService.java`、`security/session/PkiSessionTicketService.java`、`service/audit/PkiContextEnricher.java`。
  - USBKey：`source/dts-platform/.../web/rest/SecurityPkiResource.java`、`domain/security/SecurityPkiBinding.java`、`SecurityPkiBindingRepository.java`。
  - 密级/脱敏：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/policy/DataLevelSqlHelper.java`、`dts-common/.../security/SecurityLevelCatalog.java`。
  - 错误屏蔽：`dts-platform/.../web/rest/errors/ExceptionTranslator.java`（确认仍不外泄堆栈/包名）。
- 回归只读验证，不改既有契约；若 `AccessChecker.canPerform` 接入点触及上述 symbol，先 `gitnexus_impact` 评估 blast radius。

## 影响范围

- `source/dts-admin/src/test/**`、`source/dts-platform/src/test/**`（回归用例）
- 回归对象既有 symbol：`PkiVerificationService`、`SecurityPkiResource`、`DataLevelSqlHelper`、`ExceptionTranslator`（改动前先 `gitnexus_impact`）
- `worklog/v2.2.3/sprint-36-202606/it/evidence/regression/`

## 验证

- [ ] PKI/USBKey 登录测试全绿，signType 路径不变。
- [ ] RLS/masking/FIELD 口径与改前一致。
- [ ] 审计事件无缺失，错误屏蔽不回退。

## 完成标准

- [ ] 回归报告证明既有安全能力零回归，归档到 evidence。
