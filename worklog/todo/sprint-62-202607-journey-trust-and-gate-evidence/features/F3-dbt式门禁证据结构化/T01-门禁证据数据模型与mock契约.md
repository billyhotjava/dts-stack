# T01: 门禁证据数据模型与 mock 契约

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
定义结构化门禁的数据模型与解析器，mock 契约先行，API 缺口显式。

## 技术设计
- 新建 `src/components/journey/gateEvidence.ts`：
  - `GateCheckKey = "standardsCoverage" | "compile" | "test" | "run"`（对应 dbt 语义：schema 约束/compile/test/run results）。
  - `GateCheckStatus = "ready" | "missing" | "blocked"`；`GateCheck = { key, label, status, detail, evidenceUrl?, apiName? }`。
  - `GateEvidence = { checks: GateCheck[]; verdict: "pass" | "warn" | "fail" }`；`resolveGateVerdict(checks)` 纯函数。
  - `buildGateEvidence(params, options?)`：根据旅程参数推导——有 standardDraftId → standardsCoverage ready（detail 引落标草稿）；有 modelId → compile ready；test 恒 missing 并标 `apiName: "GET /api/dbt/test-results"`（后端缺口）；有 runId → run ready，否则 missing 标 `GET /api/ops/run-evidence`。
- verdict 规则写死并测试：blocked>missing>ready。

## 影响范围
- 新增 `gateEvidence.ts` + `GateEvidence.source-contract.test.ts`
- `index.ts` 导出

## 验证
- [ ] 各参数组合下 checks 状态与 verdict；apiName 在 missing/blocked 时必填。

## 完成标准
- [ ] 纯函数零 React 依赖；测试 GREEN；build 通过。
