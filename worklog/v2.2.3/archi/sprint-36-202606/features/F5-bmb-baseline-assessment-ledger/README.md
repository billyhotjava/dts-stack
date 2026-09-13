# F5: BMB17.x 符合性映射与测评整改台账

**优先级**: P0
**状态**: READY

## 目标

按协议 2.3.2.10「机密级」要求，将既有 `SecurityBaselineService` 的 6 项笼统自查基线升级为对齐 BMB17.1/17.2-2024 条款的可追溯检查项，并把整改台账扩展为支持甲方指定机构离线+在线测评、两轮迭代整改闭环与证据包导出的合规底座。当前缺口是全仓无 BMB17.x 条款映射、无测评机构对接与整改留痕。

## 协议依据与缺口

- 协议条款：2.3.2.10-1（按 BMB17.1/17.2-2024 机密级设计开发）、2.3.2.10-2（第三方离线+在线测评整改至通过，两轮迭代）。
- 当前缺口（带证据，见 `assets/gap-evidence/M10-安全保密.md`）：
  - 全仓 grep `BMB17 / 分级保护 / 离线测评 / 在线测评 / 安全检测` 0 命中（M10 §二 2.3.2.10-1 缺口 1）。
  - `SecurityBaselineService.java:24-79` 仅 6 项笼统基线（5 项 MANUAL，仅 `SEC_BASELINE_AUDIT_LOG` 为 AUTO），未对齐 BMB17.x 条款编号。
  - 无测评机构对接、测评/检测报告、整改闭环两轮迭代记录（M10 §二 2.3.2.10-2 全红）。
  - 已有底座：`SecurityBaselineResource.java`(`/api/security/baseline`)、`SecurityBaselineRemediation.java`、`exportReport()`（`SecurityBaselineService.java:127`）可承载整改台账，但状态机仅 NOT_STARTED/IN_PROGRESS/DONE/WAIVED 单轮。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | DEFINITIONS 扩展为 BMB17.x 条款可追溯检查项 | P0 | READY | F1,F2,F3 |
| T02 | BMB17.x 条款↔代码/配置符合性对照表与台账 seed | P0 | READY | T01 |
| T03 | 测评整改工作流：状态机 + 两轮迭代录入 | P0 | READY | T01 |
| T04 | exportReport 升级为测评整改证据包导出 | P0 | READY | T02,T03 |
| T05 | 集成测试：映射完整性/AUTO 判定/两轮流转/证据包 | P0 | READY | T01-T04 |

## 完成标准

- [ ] 每个检查项含 BMB17.x 条款号、控制目标、检查方式（AUTO/MANUAL）与代码/配置证据指针。
- [ ] 产出条款↔实现对照表（assets 文档 + 台账数据 seed），覆盖口令/会话/操作权限/错误屏蔽/加密/审计/TLS。
- [ ] 整改工作流支持离线+在线两轮迭代，记录责任人、闭环时间、复测结论。
- [ ] 证据包导出含条款、状态、证据指针、整改记录与两轮迭代留痕，可供甲方测评机构归档。
- [ ] AUTO 检查项可自动判定，集成测试覆盖映射完整性与状态机流转。

## TDD 约定

- 每个 task 遵循 RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，状态机/AUTO 判定/证据包导出路径要求分支覆盖。
- 改既有 symbol（`SecurityBaselineService`、`SecurityBaselineRemediation`、`exportReport`、`SecurityBaselineResource`）前先 `gitnexus_impact`；Java 侧禁用 `Optional.get()`，统一用 `orElseThrow()`。
