# T01: DEFINITIONS 扩展为 BMB17.x 条款可追溯检查项

**优先级**: P0
**状态**: READY
**依赖**: F1, F2, F3

## 目标

将 `SecurityBaselineService.DEFINITIONS`（现 6 项笼统基线）扩展为按 BMB17.1/17.2-2024 机密级条款编号组织的可追溯检查项，每项携带条款号、控制目标、检查方式（AUTO/MANUAL）与对应代码/配置证据指针。

## TDD 测试先行（RED）

- 新增 `SecurityBaselineDefinitionsTest`（放 `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/baseline/`）。
- 断言：`listChecks()` 返回项数 > 6；每个 DTO 的 `checkKey` 形如 `BMB17_*` 且唯一；新增字段 `clauseCode`（如 `BMB17.2-2024-7.x`）、`controlObjective`、`evidencePointer` 均非空。
- 断言：`type` 仅取 `AUTO`/`MANUAL`，且至少口令/会话/审计/TLS 各有一项；MANUAL 项 `evidencePointer` 指向真实配置文件路径。
- 断言：原 4 个状态值 NOT_STARTED/IN_PROGRESS/DONE/WAIVED 经 `normalizeStatus` 仍合法（回归不破坏）。

## 技术设计（GREEN）

- 扩展 record `BaselineDefinition`（`SecurityBaselineService.java:206-214`）增字段 `clauseCode`、`controlObjective`、`evidencePointer`。
- 重写 `DEFINITIONS`（`SecurityBaselineService.java:24-79`）：按条款映射口令(F1)、会话(F2)、操作权限(F3)、错误屏蔽、加密、审计、TLS 等检查项，证据指针引用 `realm-dts.json`、`PortalSessionInactivityFilter.java`、`ExceptionTranslator.java`、`DataStandardCrypto.java`、`application-tls.yml` 等真实路径。
- 同步扩展 `SecurityBaselineCheckDto`（`.../baseline/dto/SecurityBaselineCheckDto.java`）新增对应 getter/setter，并在 `toDto`（`SecurityBaselineService.java:160`）填充。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineService.java`（改既有 symbol，需 gitnexus_impact）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/baseline/dto/SecurityBaselineCheckDto.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineDefinitionsTest.java`（新增）

## 验证

- [ ] 检查项总数与条款映射覆盖口令/会话/操作权限/错误屏蔽/加密/审计/TLS。
- [ ] 每项 DTO 含 `clauseCode`/`controlObjective`/`evidencePointer` 且非空。
- [ ] AUTO/MANUAL 分类正确，MANUAL 项证据指针指向真实路径。

## 完成标准

- [ ] DEFINITIONS 由 6 项笼统基线升级为按 BMB17.x 条款编号的可追溯检查项，回归测试通过。
