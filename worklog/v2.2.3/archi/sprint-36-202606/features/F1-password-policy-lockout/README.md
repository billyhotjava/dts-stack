# F1: 口令策略与登录失败锁定

**优先级**: P0
**状态**: READY

## 目标
闭合机密级最基础的口令控制：Keycloak realm 当前**无强口令策略、暴力破解保护未启用**，存在弱口令注册与账号被暴力枚举/爆破的风险。本 feature 为 realm 配置 `passwordPolicy` 与失败锁定，并在 `dts-admin` 启动时做策略存在性校验，防止环境漂移把策略改没。

## 协议依据与缺口
- 协议条款：2.3.2.10（安全保密）+ BMB17.1/17.2-2024 机密级口令控制；衍生整改项 2.3.2.10-4（强密码策略 / 登录失败锁定）。
- 当前缺口（带证据，底稿 `assets/gap-evidence/M10-安全保密.md`）：
  - `services/dts-keycloak/realm-dts.json` **无 `passwordPolicy` 字段**（grep count=0），未配置长度/复杂度/有效期/历史口令。
  - 同 realm `bruteForceProtected: false`（line 40）、`permanentLockout: false`（line 41），暴力破解保护未启用；`failureFactor: 30`（line 49）、`waitIncrementSeconds: 60`（line 46）、`maxFailureWaitSeconds: 900`（line 44）等阈值虽存在但因保护关闭而失效。
  - sprint-32~35 **未闭合**该项（newlyClosed=无）。

## Task 列表
| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 配置 realm passwordPolicy（长度/复杂度/历史/有效期/不含用户名） | P0 | READY | — |
| T02 | 启用 bruteForceProtected + 失败锁定阈值整定 | P0 | READY | T01 |
| T03 | dts-admin 启动时口令/锁定策略存在性校验（fail-fast） | P0 | READY | T01,T02 |
| T04 | 前端登录/改密页展示口令要求与锁定提示（本地化、不泄露账号是否存在） | P1 | READY | T01,T02 |
| T05 | 集成测试：弱口令被拒、连续失败触发锁定、锁定期被拒、解锁恢复 | P0 | READY | T01-T04 |

## 完成标准
- [ ] realm `passwordPolicy` 含长度≥12、大写+小写+数字+特殊字符、口令历史≥5、最长有效期、不含用户名。
- [ ] realm `bruteForceProtected: true`，失败锁定阈值整定到机密级合理值并生效。
- [ ] `dts-admin` 启动校验：策略缺失或被改弱时 fail-fast，启动失败并给出明确日志。
- [ ] 前端展示口令规则与锁定提示，错误信息本地化且不暴露账号是否存在。
- [ ] 集成测试覆盖弱口令拒绝、连续失败锁定、锁定期拒绝、解锁恢复全链路。

## TDD 约定
- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，鉴权/口令/会话路径要求分支覆盖。
- 改既有 symbol 前先 `gitnexus_impact`；Java 侧禁用 `Optional.get()`，统一用 `orElseThrow()`。
