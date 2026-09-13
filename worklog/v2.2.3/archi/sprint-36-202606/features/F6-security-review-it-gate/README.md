# F6: 安全合规评审、回归与 IT 准入

**优先级**: P0
**状态**: READY

## 目标

作为 Sprint-36 的质量门禁与发布准入，对 F1-F5 的全部安全敏感改动（口令策略/失败锁定、会话整改、操作权限矩阵、敏感识别、BMB 台账）做收口评审、覆盖率门禁、IT 证据归档与回归验证，确保机密级测评所需证据齐全、既有安全能力不回归。本 feature 不引入独立协议新功能，聚焦评审/回归/IT 证据闭环（2.3.2.5 + 2.3.2.10 整体验收；2.3.3-12 测试完备性）。

## 协议依据与缺口

- 协议条款：2.3.2.5 + 2.3.2.10 整体安全合规验收；2.3.3-12 测试完备性。
- 当前缺口（带证据）：依据 `assets/gap-evidence/M10-安全保密.md`，第三方测评整改（2.3.2.10-2）「全仓未找到」离线/在线测评、整改闭环台账证据（line 56-59）；衍生整改项强密码、失败锁定为 🔴 缺口（line 78-79）；会话控制 P0（前端裸 token、`TEST_SESSION_ENABLED`/`handleDevFallback` 旁路）未闭合（line 76）。F6 负责验证 F1-F5 已闭合上述项且 PKI/CA + USBKey（2.3.2.10-3，line 61-69）、`DataLevelSqlHelper` 密级隔离与 `ExceptionTranslator` 错误屏蔽不回归。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | security-reviewer 全量复审 F1-F5 安全敏感改动 | P0 | READY | F1-F5 |
| T02 | 覆盖率门禁与 run_gates.sh 接入 | P0 | READY | F1-F5 |
| T03 | IT 证据归档到 it/evidence/ | P0 | READY | F1-F5 |
| T04 | 回归验证（PKI/RLS/masking/审计不回归） | P0 | READY | F1-F5 |
| T05 | 发布准入 checklist | P0 | READY | T01-T04 |

## 完成标准

- [ ] security-reviewer 对 F1-F5 鉴权/口令/会话/识别改动复审，无 CRITICAL/HIGH 方可准入。
- [ ] M05/M10 改动模块覆盖率 ≥80%，口令/会话/`canPerform` 鉴权路径达到分支覆盖。
- [ ] IT 证据覆盖弱口令被拒、失败锁定、越权拦截、敏感扫描命中、BMB 台账导出五类场景并落 `it/evidence/`。
- [ ] PKI/CA + USBKey 登录与既有 RLS/masking/FIELD 策略、审计链路经回归验证不破坏。
- [ ] 发布准入 checklist 全绿，`gitnexus_detect_changes` 影响范围符合预期。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，鉴权/口令/会话路径要求分支覆盖。
- 改既有 symbol 前先 `gitnexus_impact`；Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`（modernizer 强制）。
