# F4: metric-pack 合作方交付工作流

**优先级**: P0
**状态**: DONE
**目标**: 让合作方在不接触平台源码的前提下，通过行业指标包交付客户贴近型指标资产。

**Sprint-31A 依赖**: metric-pack 导入校验必须调用 platform asset contract 和 permission check，合作方包不得携带任意 SQL 或未登记资产引用。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | metric-pack schema v0.1 | 定义 manifest、domains、objects、dimensions、metrics、models、datasets、dashboards |
| T02 | 包导入和校验 | 校验版本、依赖、资产引用、字段存在性、公式 DSL、安全规则 |
| T03 | 包预览和差异报告 | 展示新增、修改、删除、冲突、依赖缺失和潜在破坏 |
| T04 | 审核发布流程 | 导入后必须经过平台审核，发布动作写审计并生成 publish record |
| T05 | 示例行业包 | 提供一个 `flower-rental` 示例包，覆盖合同、回款和项目风险的最小闭环 |

## 完成标准

- [x] 合作方交付物是配置包，不是平台源码。
- [x] 无法通过 metric-pack 携带任意 SQL 或不安全资产引用；真实资产存在性由 platform contract/permission 统一校验。
- [x] 包发布失败时可以回滚到上一版本；Sprint-32 先提供 dry-run 和候选 artifact。
- [x] dashboard 配置只作为元数据校验，不在 Sprint-32 承诺自动生成完整大屏。

## 证据

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricPackValidationService.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/web/rest/MetricPackResource.java`
- `source/dts-metrics/src/test/java/com/yuzhi/dts/metrics/service/MetricPackValidationServiceTest.java`
- `worklog/v2.2.3/sprint-32-202605/assets/examples/flower-rental/`
- `worklog/v2.2.3/sprint-32-202605/it/evidence/metric-pack/README.md`
