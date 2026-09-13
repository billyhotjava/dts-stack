# T01: security-reviewer 全量复审 F1-F5 安全敏感改动

**优先级**: P0
**状态**: READY
**依赖**: F1-F5

## 目标

对 F1-F5 全部鉴权/口令/会话/敏感识别改动做 security-reviewer 全量复审，输出分级问题清单，CRITICAL/HIGH 清零方可放行进入 IT 准入。

## TDD 测试先行（RED）

- 先写复审「断言型」测试，把评审项编码为可执行用例，运行确认 FAIL：
  - `KeycloakRealmPolicyAssertionTest`（test 包 `com.yuzhi.dts.admin.security`）：断言 `services/dts-keycloak/realm-dts.json` 含 `passwordPolicy` 且 `bruteForceProtected=true`、`permanentLockout`/失败阈值已配置（对照 M10 line 78-79 缺口）。
  - `SessionBypassGuardTest`（`com.yuzhi.dts.platform.security.session`）：断言生产 profile 下 `TEST_SESSION_ENABLED`/`handleDevFallback` 旁路硬关闭、无 `console.log(Authorization)`（对照 M10 line 76 / sprint-22 评审）。
  - `AccessCheckerDenyByDefaultTest`（`...security.access`）：断言 `AccessChecker.canPerform` 对未配置动作默认拒绝，无放行漏洞。
- 评审结论以「无 CRITICAL/HIGH」为绿灯断言，存在即 FAIL。

## 技术设计（GREEN）

- 用 security-reviewer 代理对 F1-F5 diff 逐模块复审，按 OWASP + 机密级口令/会话/鉴权清单产出 CRITICAL/HIGH/MEDIUM/LOW 分级。
- 复审范围锚点（真实路径）：
  - 口令/锁定：`services/dts-keycloak/realm-dts.json`、`dts-admin` 启动校验。
  - 会话：`source/dts-platform/.../security/session/PortalSessionInactivityFilter.java`、`PortalSessionRegistry.java`、`dts-admin/.../web/filter/SessionInactivityFilter.java`、前端 token 存储。
  - 权限：新增 `IamAssetActionPolicy` + `AccessChecker.canPerform`（复用审计 `OperationType` 语义）。
  - 识别：`SensitiveScanService`，重点核「扫描不外泄样本数据」。
- 复审记录与整改回填存入 `it/evidence/security-review/`。

## 影响范围

- 复审对象：`source/dts-platform/src/main/java/**`、`source/dts-admin/src/main/java/**`、`services/dts-keycloak/realm-dts.json`、相关 `*-webapp/src/**`（F1-F5 改动既有 symbol，复审前先 `gitnexus_impact`）。
- 新增证据：`worklog/v2.2.3/sprint-36-202606/it/evidence/security-review/`

## 验证

- [ ] CRITICAL/HIGH 计数为 0，MEDIUM 有处置结论。
- [ ] 三个断言测试 GREEN，覆盖口令/会话/默认拒绝三条主线。
- [ ] 复审清单与整改回填可追溯到 commit。

## 完成标准

- [ ] security-reviewer 出具「准入通过」结论，作为 T05 checklist 的输入项。
