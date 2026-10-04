# T01: realm 口令策略与暴力破解防护配置

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
`realm-dts.json` 具备 BMB 机密级基线要求的 `passwordPolicy` 与暴力破解防护配置，且导入到 Keycloak 后实际生效。

## 技术设计 (Contract-first)
- **输入契约**: 现状 realm（账本#1）——无 `passwordPolicy`，`bruteForceProtected:false`。
- **输出契约**（realm JSON 顶层字段）:
  ```json
  "passwordPolicy": "length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and notEmail and passwordHistory(5) and forceExpiredPasswordChange(90) and hashAlgorithm(pbkdf2-sha512) and hashIterations(210000)",
  "bruteForceProtected": true,
  "permanentLockout": false,
  "failureFactor": 5,
  "waitIncrementSeconds": 60,
  "quickLoginCheckMilliSeconds": 1000,
  "minimumQuickLoginWaitSeconds": 60,
  "maxFailureWaitSeconds": 900,
  "maxDeltaTimeSeconds": 43200
  ```
- **数据流**: realm JSON → keycloak 容器导入 → 认证时由 Keycloak 强制执行。应用侧零改动。
- **错误路径**:
  - realm 导入失败（策略串语法错误）→ 容器起不来。**必须先在一次性容器里验证策略串**再提交。
  - 现有存量用户口令不满足新策略 → Keycloak 不会追溯拒绝已存在口令，仅在下次改密时生效；`forceExpiredPasswordChange(90)` 会让存量用户在 90 天内被迫改密，须在发布说明中写明。
- **复用点**: 沿用现有 realm 导入链路（compose `dts-keycloak` 服务），不新增初始化脚本。
- **实现方案**:
  1. 一次性容器验证策略串：`docker run --rm keycloak ... --import-realm` 观察启动日志
  2. 改 `realm-dts.json`
  3. 写 `assets/release-plan.md`：变更前备份 realm 导出、失败回滚步骤（导入旧 realm JSON）
  4. **数值标注「待甲方确认」**（README 开放问题1）；确认前用本表默认值

## 影响范围
- `services/dts-keycloak/realm-dts.json`
- `worklog/.../assets/release-plan.md`（新建）
- 潜在影响：所有本地账号登录用户；PKI 登录路径不受影响（不走口令）

## 验证 (RED→GREEN)
- [ ] RED：改前用 `abc` 作为新口令能改密成功
- [ ] GREEN：改后同一操作被拒，错误信息指明违反的策略项
- [ ] 边界：正好 12 位且满足全部字符类 → 通过；11 位 → 拒绝
- [ ] 回归：PKI 登录路径不受影响（sprint-78 ADR-78-09 提到的仅 PKI 现场）

## Definition of Done
- [ ] 架构：realm 导入成功，Keycloak admin 控制台可见策略；回滚步骤已实测
- [ ] UI：改密失败提示为中文可读文案（与 T03 联调）
- [ ] 切片：真实实例上完成一次「弱口令被拒 → 合规口令通过」
- [ ] 无占位证据
