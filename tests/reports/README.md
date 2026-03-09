# Test Reports

`tests/reports/` 存放由 `tests/run_suite.py` 与 `tests/run_gates.sh` 生成的结构化报告。

## 产物类型

- `*-<suite>.json`：suite 原始结果，适合作为 dashboard 或二次处理输入
- `*-<suite>.md`：人类可读摘要
- `*-<suite>.xml`：JUnit 兼容结果
- `*-gates-summary.json`：gate 汇总结果，包含跨 suite 质量指标
- `*-gates-summary.md`：gate 人类可读摘要

## Quarantine 规则

- `web-e2e-quarantine` 只承载已确认 flaky 的浏览器用例
- quarantine 用例必须声明：
  - `owner`
  - `quarantine.reason`
  - `quarantine.slaHours`
- quarantine 用例默认不进入 `pr` / `nightly` / `release` gate
- 需要单独运行时使用：

```bash
bash tests/run_gates.sh --with-web-e2e-quarantine --include-optional
```

## 关键指标

`run_suite.py` 现在会输出：

- `metrics.passRate`
- `metrics.ownerCoverage`
- `metrics.quarantineCaseCount`
- `metrics.quarantineFailureCount`
- `businessJourneys[]`

`run_gates.sh` 会把各 suite 的指标汇总到 `qualityMetrics`，用于后续质量运营看板。
