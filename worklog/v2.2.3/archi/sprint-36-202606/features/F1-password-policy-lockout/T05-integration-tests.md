# T05: 集成测试 — 弱口令拒绝与失败锁定全链路

**优先级**: P0
**状态**: READY
**依赖**: T01, T02, T03, T04

## 目标
端到端验证强口令策略与失败锁定真实生效：弱口令注册/改密被拒、连续失败 N 次触发锁定、锁定期登录被拒、解锁后恢复。

## TDD 测试先行（RED）
- 新增 `PasswordPolicyLockoutIT`（`source/dts-admin/src/test/java/com/yuzhi/dts/admin/web/`，复用 `IntegrationTest` 基座）：
  - **弱口令被拒**：以 `length<12`/缺特殊字符/含用户名 的口令注册/改密，断言返回策略校验失败码，账号未被创建/口令未变更。
  - **连续失败触发锁定**：对同一账号连续错误登录达 `failureFactor`（机密级阈值）次，断言账号进入临时锁定。
  - **锁定期被拒**：锁定窗口内即使输入正确口令也被拒，错误信息泛化不泄露账号状态。
  - **解锁后恢复**：等待 `waitIncrementSeconds`/`maxFailureWaitSeconds` 窗口或管理员解锁后，正确口令可成功登录。
- 锁定计时用可注入时钟或缩短的测试态阈值，避免真实等待 900s。

## 技术设计（GREEN）
- 集成测试基于 T01/T02 的 realm 策略、T03 的启动校验组装运行态。
- 通过 admin 登录/改密入口驱动：`KeycloakApiResource`、`service/keycloak/KeycloakAuthService.java`、`KeycloakAdminClient`；以 `InMemoryKeycloakAdminClient` 模拟 realm 锁定状态机用于 CI。
- 断言审计留痕：失败/锁定/解锁事件落审计（对齐底稿基线项 `SEC_BASELINE_AUDIT_LOG`），不写入明文口令。
- 证据归档到 `worklog/v2.2.3/sprint-36-202606/it/evidence/password-policy-lockout/`。

## 影响范围
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/web/PasswordPolicyLockoutIT.java`（新增）
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/IntegrationTest.java`（复用基座）
- `worklog/v2.2.3/sprint-36-202606/it/evidence/password-policy-lockout/`（证据）

## 验证
- [ ] 弱口令注册/改密被拒，账号/口令未变更。
- [ ] 连续失败达阈值触发临时锁定，锁定期正确口令亦被拒。
- [ ] 解锁/窗口过后正确口令恢复登录。
- [ ] 失败/锁定/解锁有审计留痕且不含明文口令。

## 完成标准
- [ ] 口令策略与失败锁定端到端验证通过，覆盖率 ≥80% 且分支覆盖鉴权/锁定路径。
