# F1: 口令策略与登录失败锁定

**优先级**: P0（协议 2.3.2.10 / BMB17.1 机密级最基础控制项）
**状态**: READY（纯配置 + 后端校验 + 前端文案，不依赖 G0 浏览器基线即可开工，UI 文案项除外）

## 目标
使用本地账号登录的用户，设置弱口令时被**真实拒绝**并看到具体不满足哪一条；连续登录失败达阈值后账号被**真实锁定**并在审计中可查。

**闭合缺口**: P0-1（强密码策略缺失）、P0-2（登录失败锁定未启用）。
**现状**（账本#1）：`realm-dts.json` 无 `passwordPolicy` 字段，`bruteForceProtected:false`、`permanentLockout:false`、`failureFactor:30`、`maxFailureWaitSeconds:900`。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| 配置 | `services/dts-keycloak/realm-dts.json` → `passwordPolicy` | 字符串形式，如 `length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and passwordHistory(5) and forceExpiredPasswordChange(90)`（数值待甲方确认，见 README 开放问题1） |
| 配置 | 同 realm → 暴力破解防护 | `bruteForceProtected:true`、`permanentLockout:false`、`failureFactor:5`、`waitIncrementSeconds:60`、`maxFailureWaitSeconds:900`、`quickLoginCheckMilliSeconds:1000`、`minimumQuickLoginWaitSeconds:60` |
| REST | `GET /api/admin/security/password-policy` | resp: `{minLength:int, requireUpper:bool, requireLower:bool, requireDigit:bool, requireSpecial:bool, historyCount:int, expireDays:int, lockoutThreshold:int, lockoutWindowSeconds:int, source:"KEYCLOAK_REALM"}` |
| Service | `KeycloakSecurityPolicyValidator`（dts-admin，新增） | 启动期读 realm 实况；不符合基线 → `log.warn` + 写审计动作 `SECURITY_POLICY_BASELINE_VIOLATION` |
| 审计 | 新动作码须在 dts-admin 审计资源字典登记（domain-dts D2，账本#16） | 否则落「未分类」 |

## UI/UX 规格

- **入口与导航**: 不新增页面。落在既有登录页 `pages/sys/login/` 与改密弹窗。
- **布局线框**:
  ```
  ┌ 修改密码 ─────────────────────┐
  │ 新密码  [____________]        │
  │ ┌ 策略提示（实时校验）───────┐ │
  │ │ ✓ 至少 12 位               │ │
  │ │ ✓ 含大写字母               │ │
  │ │ ✗ 含特殊字符               │ │
  │ │ ✗ 不得与最近 5 次重复      │ │
  │ └────────────────────────────┘ │
  │            [取消]  [确定]      │
  └────────────────────────────────┘
  ```
- **四态**:
  - 空：未输入时策略清单全部灰色中性态，不显示红叉
  - 加载：策略接口未返回时显示骨架，不阻断输入
  - 错误：策略接口 5xx → 降级为静态兜底文案 + 提交仍由 Keycloak 兜底拒绝（**不得因前端拿不到策略就放行**）
  - 成功：全部 ✓ 后确定按钮可用
- **关键交互**: 输入实时本地校验（长度/字符类）；历史重复只能由 Keycloak 在提交时判定，失败后把 Keycloak 的错误码映射为中文文案。
- **锁定反馈**: 达阈值后登录页提示「账号已锁定，请 X 分钟后重试或联系管理员」，**不得泄露账号是否存在**。
- **操作走查**: 1. 打开改密 → 2. 输入 `abc123` → 3. 看到 4 条 ✗ 且确定不可点 → 4. 输入合规口令 → 5. 提交成功；另：连续 5 次错误口令登录 → 第 6 次提示已锁定。
- **兼容**: Chrome95；策略提示区不得使用新 CSS 特性。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | realm 口令策略与暴力破解防护配置 | P0 | READY | - |
| T02 | 登录失败锁定实证与审计留痕 | P0 | READY | T01 |
| T03 | 策略查询接口与前端策略提示 | P0 | READY | T01 |

## Definition of Ready
- [x] 契约已钉死（realm 字段 + REST DTO + 审计动作码）
- [x] 竖切片已画通（realm 配置 → admin 校验 → REST → 登录/改密 UI）
- [x] UI 落点已命名（登录页 + 改密弹窗）
- [x] 依赖已就绪（Keycloak 是既有认证 owner）
- [x] 验收可验证（弱口令被拒 / 连续失败被锁，均为可执行走查）

## 完成标准
- [ ] 弱口令被 Keycloak 真实拒绝（截图 + 服务端日志）
- [ ] 连续失败达阈值账号真实锁定，审计可查到锁定事件
- [ ] `GET /api/admin/security/password-policy` 返回值与 realm 实况一致（改 realm 后重启，接口值随之变化）
- [ ] realm 变更有回滚预案（`assets/release-plan.md`）
