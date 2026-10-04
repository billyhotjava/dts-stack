# T03: dts-admin 启动时口令/锁定策略存在性校验

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标
`dts-admin` 启动时校验运行态 realm 的 `passwordPolicy` 与暴力破解保护已配置且不弱于基线，缺失即 fail-fast，避免环境漂移把策略改没而无人察觉。

## TDD 测试先行（RED）
- 新增 `SecurityPolicyStartupValidatorTest`（`source/dts-admin/src/test/java/com/yuzhi/dts/admin/config/`）：
  - mock realm 配置缺 `passwordPolicy` 或 `bruteForceProtected=false` 时，校验器抛出启动期异常（`IllegalStateException`），应用上下文启动失败。
  - 策略齐备且达标时校验通过、不抛异常。
  - 校验失败日志含明确条目（缺哪项、期望值），但**不输出口令/令牌等敏感值**。
- 用 `InMemoryKeycloakAdminClient` 构造缺失/达标两组夹具，分支覆盖 ≥80%。

## 技术设计（GREEN）
- 新增 `SecurityPolicyStartupValidator`（`source/dts-admin/src/main/java/com/yuzhi/dts/admin/config/`），实现 `ApplicationRunner`/`InitializingBean`，启动时拉取 realm 表示并断言 `passwordPolicy` 含长度≥12+复杂度+历史、`bruteForceProtected=true`、`failureFactor` 达标。
- 拉取 realm 配置经 `KeycloakAdminClient`（`service/keycloak/KeycloakAdminClient.java`，实现 `KeycloakAdminRestClient`/`InMemoryKeycloakAdminClient`）：若需新增 `getRealmConfiguration()` 接口方法，**先 `gitnexus_impact({target:"KeycloakAdminClient", direction:"downstream"})`** 评估两实现与调用方。
- Optional 取值统一 `orElseThrow()`，禁用 `.get()`。
- 阈值期望值抽到 `InfraSecurityProperties.java` 或新增配置项，便于环境差异化但保留下限。

## 影响范围
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/config/SecurityPolicyStartupValidator.java`（新增）
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/keycloak/KeycloakAdminClient.java`（如扩接口，需 gitnexus_impact）
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/keycloak/{KeycloakAdminRestClient,InMemoryKeycloakAdminClient}.java`（同步实现）
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/config/SecurityPolicyStartupValidatorTest.java`（新增）

## 验证
- [ ] 策略缺失/被改弱时启动 fail-fast，日志明确且不泄露敏感值。
- [ ] 策略达标时启动正常通过。
- [ ] 接口扩展前已跑 gitnexus_impact 并报告影响面。

## 完成标准
- [ ] 启动校验落地，环境漂移可被即时拦截。
