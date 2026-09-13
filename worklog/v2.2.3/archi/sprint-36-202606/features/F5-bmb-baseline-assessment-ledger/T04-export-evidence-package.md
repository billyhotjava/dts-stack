# T04: exportReport 升级为测评整改证据包导出

**优先级**: P0
**状态**: READY
**依赖**: T02, T03

## 目标

将 `exportReport()` 由 6 项基线 Markdown 表升级为面向甲方测评机构归档的测评整改证据包：含条款号、控制目标、检查方式/状态、证据指针、整改记录与两轮迭代留痕。

## TDD 测试先行（RED）

- 新增 `SecurityBaselineReportExportTest`（放 `.../service/security/baseline/`）。
- 断言：`exportReport()` 返回的 `checks` 每项含 `clauseCode`、`controlObjective`、`evidencePointer`（T01）与 `assessmentRound`/`retestConclusion`（T03）。
- 断言：生成的 Markdown 含「BMB17.x 条款」「证据指针」「第一轮/第二轮整改」表头；DONE 项体现复测结论，WAIVED 项体现豁免说明。
- 断言：`generatedAt` 为 ISO_INSTANT；导出不泄露内部堆栈/明文密钥（与 `ExceptionTranslator` 屏蔽一致的脱敏约定）。

## 技术设计（GREEN）

- 改造 `exportReport()`（`SecurityBaselineService.java:127-158`）：表头列扩展为 条款号/控制目标/检查方式/状态/证据指针/整改记录/迭代轮次/复测结论；遍历 T03 迭代记录拼接两轮留痕。
- 复用 `escapeCell`（`SecurityBaselineService.java:192`）做单元格转义；`Optional` 取值统一 `orElseThrow()`/`orElse(...)`，禁用 `get()`。
- `SecurityBaselineResource.exportReport`（`SecurityBaselineResource.java:64`，`GET /api/security/baseline/report`）维持 `INSTITUTE_PRIVILEGED_EXPRESSION` 鉴权，并保留 `SECURITY_BASELINE_REPORT_VIEW` 审计动作码。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineService.java`（改既有 symbol，需 gitnexus_impact）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SecurityBaselineResource.java`（改既有 symbol，需 gitnexus_impact）
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/baseline/SecurityBaselineReportExportTest.java`（新增）

## 验证

- [ ] 证据包含条款号/证据指针/整改记录/两轮迭代留痕，结构完整可归档。
- [ ] 导出内容脱敏，不泄露堆栈或明文密钥。
- [ ] `/report` 端点鉴权与审计动作码保持不变。

## 完成标准

- [ ] exportReport 产出可供甲方测评机构归档的完整证据包，导出测试通过。
