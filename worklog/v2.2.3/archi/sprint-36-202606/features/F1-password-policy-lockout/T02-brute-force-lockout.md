# T02: 启用 bruteForceProtected 与失败锁定阈值

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
开启 realm 暴力破解保护并整定失败锁定阈值，使连续登录失败触发临时锁定，杜绝账号爆破。

## TDD 测试先行（RED）
- 新增 `RealmBruteForceContractTest`（`source/dts-admin/src/test/java/com/yuzhi/dts/admin/config/`）：解析 realm JSON，断言 `bruteForceProtected == true`，且锁定参数在机密级合理区间。当前应**失败**（line 40 为 false）。
- 断言阈值字段齐备：`failureFactor`（最大失败次数）、`waitIncrementSeconds`、`maxFailureWaitSeconds`、`quickLoginCheckMilliSeconds`、`minimumQuickLoginWaitSeconds` 均存在且 >0。
- 断言 `failureFactor` 由现值 30 收紧到机密级阈值（如 ≤5），避免锁定形同虚设。

## 技术设计（GREEN）
- 在 `services/dts-keycloak/realm-dts.json` 将 `bruteForceProtected` 改为 `true`（line 40）。
- 整定既有阈值字段（已存在但因保护关闭而失效）：`failureFactor`（line 49，30→机密级阈值）、`waitIncrementSeconds`（line 46）、`maxFailureWaitSeconds`（line 44）、`quickLoginCheckMilliSeconds`（line 47）、`minimumQuickLoginWaitSeconds`（line 45）。
- `permanentLockout`（line 41）维持 `false`，采用临时锁定 + 递增等待；如机密级要求永久锁定再单独评审。

## 影响范围
- `services/dts-keycloak/realm-dts.json`（line 40-49 阈值整定）
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/config/RealmBruteForceContractTest.java`（新增）

## 验证
- [ ] `bruteForceProtected: true` 且锁定逻辑生效。
- [ ] `failureFactor` 收紧到机密级阈值，等待递增参数齐备。
- [ ] 临时锁定策略明确（permanentLockout 维持 false 并有依据）。

## 完成标准
- [ ] 暴力破解保护启用并被 contract 测试守护。
