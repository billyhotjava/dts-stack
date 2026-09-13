# T02: BMB17.x 条款↔代码/配置符合性对照表与台账 seed

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

产出 BMB17.1/17.2-2024 条款 ↔ 代码/配置符合性对照表（assets 文档 + 台账数据 seed），覆盖口令(F1)、会话(F2)、操作权限(F3)、错误屏蔽、加密、审计、TLS，使每条条款可定位到具体实现证据。

## TDD 测试先行（RED）

- 新增 `SecurityBaselineSeedTest`（放 `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/baseline/`）。
- 断言：seed 中每个 `checkKey` 都能在 T01 的 DEFINITIONS 中找到对应项（无孤儿台账行）。
- 断言：对照表文档每行条款都有非空证据指针，且引用的源码/配置路径在仓库内真实存在（用相对路径白名单校验）。
- 断言：覆盖类别集合至少包含 口令/会话/操作权限/错误屏蔽/加密/审计/TLS 七类。

## 技术设计（GREEN）

- 新增 assets 对照表文档 `worklog/v2.2.3/sprint-36-202606/assets/bmb17-compliance-matrix.md`：列 = 条款号 / 控制目标 / 检查方式 / 实现证据指针 / 状态。
- 证据指针引用真实路径：口令 `services/dts-keycloak/realm-dts.json`、会话 `PortalSessionInactivityFilter.java`/`PortalSessionRegistry.java`、错误屏蔽 `web/rest/errors/ExceptionTranslator.java`、加密 `service/modeling/DataStandardCrypto.java`/`service/infra/InfraSecretService.java`、审计 `service/audit/AuditService`、TLS `src/main/resources/config/application-tls.yml`、密级 `dts-common/.../security/SecurityLevelCatalog.java`。
- 新增 liquibase seed `20260608_xx_security_baseline_bmb17_seed.xml` 预置台账行（`security_baseline_remediation`），并在 `config/liquibase/master.xml` 注册（master 当前已注册 `20260102_40`）。

## 影响范围

- `worklog/v2.2.3/sprint-36-202606/assets/bmb17-compliance-matrix.md`（新增）
- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260608_xx_security_baseline_bmb17_seed.xml`（新增）
- `source/dts-platform/src/main/resources/config/liquibase/master.xml`（改既有，需 gitnexus_impact）
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineSeedTest.java`（新增）

## 验证

- [ ] 对照表覆盖口令/会话/操作权限/错误屏蔽/加密/审计/TLS 七类。
- [ ] 每条条款证据指针指向仓库内真实存在的路径。
- [ ] seed 台账行 checkKey 与 T01 DEFINITIONS 一一对应，无孤儿行。

## 完成标准

- [ ] 条款↔实现对照表与台账 seed 落地，liquibase 在 master 注册可执行。
