# T01: 配置 Keycloak realm passwordPolicy

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标
为 `S10` realm 配置强口令策略，杜绝弱口令注册与改密：长度≥12、大写+小写+数字+特殊字符、口令历史≥5、最长有效期、不含用户名。

## TDD 测试先行（RED）
- 新增 `RealmPasswordPolicyContractTest`（放 `source/dts-admin/src/test/java/com/yuzhi/dts/admin/config/`）：解析 `services/dts-keycloak/realm-dts.json`，断言 `passwordPolicy` 字段存在且包含 `length(12)`、`upperCase(1)`、`lowerCase(1)`、`digits(1)`、`specialChars(1)`、`passwordHistory(5)`、`forceExpiredPasswordChange(...)`、`notUsername`。当前应**失败**（grep count=0）。
- 反例断言：不得出现 `length(8)` 等弱于机密级基线的取值。

## 技术设计（GREEN）
- 在 realm JSON 顶层补 `"passwordPolicy"` 字符串，Keycloak 语法用 ` and ` 连接：
  `length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and passwordHistory(5) and forceExpiredPasswordChange(90) and notUsername(undefined)`。
- 有效期天数（`forceExpiredPasswordChange`）抽为可调，prod 取机密级要求值，避免硬编码散落。
- 仅改配置，不改用户已有口令；策略对后续注册/改密生效。

## 影响范围
- `services/dts-keycloak/realm-dts.json`（顶层新增 `passwordPolicy`，realm `"S10"`，line 3 起）
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/config/RealmPasswordPolicyContractTest.java`（新增）

## 验证
- [ ] realm `passwordPolicy` 存在且含长度≥12 与四类字符复杂度。
- [ ] 含 `passwordHistory(5)` 与有效期、`notUsername`。
- [ ] 弱取值（length<12）不出现在配置中。

## 完成标准
- [ ] realm 强口令策略落盘并被 contract 测试守护。
