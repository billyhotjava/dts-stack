# T02: 覆盖率门禁与 run_gates.sh 接入

**优先级**: P0
**状态**: READY
**依赖**: F1-F5

## 目标

为 M05/M10 改动模块建立覆盖率门禁：整体 ≥80%，口令/会话/`canPerform` 鉴权路径达到分支覆盖，并接入统一门禁脚本 `tests/run_gates.sh`。

## TDD 测试先行（RED）

- 先补齐分支覆盖缺口测试，运行确认未达标时门禁 FAIL：
  - `PasswordPolicyValidationTest`（`com.yuzhi.dts.admin.security`）：覆盖弱口令拒绝/强口令通过/历史口令复用/有效期过期分支。
  - `BruteForceLockoutTest`：覆盖连续失败累加、达阈值锁定、锁定窗口过期解锁分支。
  - `SessionInactivityBranchTest`（`...security.session`）：覆盖空闲未超时/超时失效/活跃续期分支。
  - `AccessCheckerCanPerformBranchTest`（`...security.access`）：覆盖 8 个动作 × 命中/未命中策略的分支。
- 门禁先期设为期望阈值，未达 80% 或鉴权分支未覆盖即 FAIL。

## 技术设计（GREEN）

- 后端 JaCoCo 配置 `dts-platform`/`dts-admin` 模块行覆盖 ≥80%，对 `security/session`、`security/access`、口令校验包追加 `branch` 覆盖规则。
- 前端 vitest 覆盖 token 存储改造与 idle 软锁逻辑 ≥80%。
- 在 `tests/run_gates.sh` 增加覆盖率检查段：聚合各模块报告、失败即非零退出，作为 CI 准入门。
- 覆盖率报告导出到 `it/evidence/coverage/`，供 T05 checklist 引用。

## 影响范围

- `source/dts-platform/**`、`source/dts-admin/**`（pom JaCoCo 规则）、`*-webapp`（vitest 配置）
- `tests/run_gates.sh`（改既有脚本前先 `gitnexus_impact`）
- `worklog/v2.2.3/sprint-36-202606/it/evidence/coverage/`

## 验证

- [ ] M05/M10 改动模块整体覆盖率 ≥80%。
- [ ] 口令/会话/`canPerform` 路径分支覆盖达标。
- [ ] `tests/run_gates.sh` 在未达标时非零退出。

## 完成标准

- [ ] 覆盖率门禁可在 CI 阻断不达标提交，报告归档可追溯。
