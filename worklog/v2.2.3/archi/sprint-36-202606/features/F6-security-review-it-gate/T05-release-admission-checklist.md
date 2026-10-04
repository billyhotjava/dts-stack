# T05: 发布准入 checklist

**优先级**: P0
**状态**: READY
**依赖**: T01-T04

## 目标

汇总 T01-T04 结论，形成 Sprint-36 发布准入 checklist：无 CRITICAL/HIGH + 机密级控制项自查通过 + `gitnexus_detect_changes` 影响范围符合预期，方可准入上线。

## TDD 测试先行（RED）

- 先把 checklist 编码为可执行准入断言，任一项未达即 FAIL：
  - `AdmissionChecklistGateTest`（test 包 `com.yuzhi.dts.platform.security`）：聚合校验 T01 复审结论（CRITICAL/HIGH=0）、T02 覆盖率门禁通过、T03 五类 evidence 齐全、T04 回归全绿。
  - `ConfidentialControlSelfCheckTest`：断言机密级最低控制项（强口令、失败锁定、会话整改、`canPerform` 默认拒绝、错误屏蔽、审计留痕）逐项自查为通过/有 WAIVED 理由。
  - 断言 `gitnexus_detect_changes()` 实际影响 symbol/flow 与预期清单一致，越界即 FAIL。

## 技术设计（GREEN）

- 产出 `it/admission-checklist.md`：逐项引用 T01-T04 证据路径（`security-review/`、`coverage/`、各场景 evidence、`regression/`）。
- 机密级控制项自查对照 `assets/gap-evidence/M10-安全保密.md` §四落地建议 1-4，逐条标注闭合状态与证据。
- 提交前运行 `gitnexus_detect_changes()`，把影响范围（受影响 symbol、执行流、风险等级）写入 checklist；HIGH/CRITICAL 风险须在准入前说明处置（见 CLAUDE.md）。
- checklist 全绿且 reviewer 签字后方可进入发布。

## 影响范围

- `worklog/v2.2.3/sprint-36-202606/it/admission-checklist.md`
- `worklog/v2.2.3/sprint-36-202606/it/README.md`
- `worklog/v2.2.3/sprint-36-202606/it/evidence/**`（引用 T01-T04 产物）

## 验证

- [ ] checklist 全部项目可勾选且各有证据路径。
- [ ] CRITICAL/HIGH 为 0，机密级控制项自查通过或有 WAIVED 说明。
- [ ] `gitnexus_detect_changes` 影响范围与预期一致，无越界 symbol。

## 完成标准

- [ ] Sprint-36 以 checklist + evidence 驱动发布准入，作为机密级测评对接入口。
