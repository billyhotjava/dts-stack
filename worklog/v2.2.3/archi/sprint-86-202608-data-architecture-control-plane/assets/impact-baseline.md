# 预研影响基线

**用途**：只用于 Sprint-86 架构讨论的范围判断，不替代实施前的逐符号 impact。
**日期**：2026-08-09

## GitNexus 状态

- `s10-stack` 初始索引落后当前 HEAD 14 个提交。
- 已按仓库规则执行 `npx gitnexus analyze`；并行解析超时后自动退化为串行，在 415 秒时长期停留 34%，为避免持续占用构建机资源已中止。
- 现有图仍可返回 symbol context/impact；以下结果必须结合当前源码行号使用，实施前必须在索引可用时重新执行。

## 预研结果

> **适用范围（RF-86-04）**：下表四个目标均为 Java 符号，结论**不外推至前端**。
> GitNexus 对本次抽查的 TypeScript/React 组件引用出现返回 `0` 的假阴性；该现象不能仅由索引落后解释，也不能外推为所有前端符号均系统性失效：
> 实测 `DomainScopeNav` 与 `SubjectAreasPage` 的 upstream impact 均为 `impactedCount: 0`，
> 而前者实有 `AssetOverviewPage.tsx:7`、`DatasetsPage.tsx:18` 两处 import，后者有 5 处活引用 + 3 条 e2e。
> 因此当前前端结论必须以图查询与 scoped `rg` 交叉验证，不能把 `0` 当作“无影响”。

| 目标 | 风险 | 影响摘要 | 架构含义 |
|---|---|---|---|
| `CatalogDomain` | MEDIUM | impacted 75、direct 10 | 已被质量、权限、资产、指标统计等直接引用；必须扩展既有实体/port，不能替换成第二套域模型 |
| `GovIndicatorDefinition` | HIGH（partial） | direct 18 | 创建、模板、版本、派生校验、发布预览、dbt 生成等均直接消费；字符串到稳定 ID 必须兼容扩展和分阶段切换 |
| `WarehouseLayerApplicationService` | LOW | impacted 15、direct 2 | canonical 分层投影已有明确 seam，可优先复用 |
| `IngestionLineageWriter` | LOW | impacted 4、direct 3 | SOURCE→origin 的写入改造调用面较集中，但会影响 ODS 生成/映射与血缘，仍需端到端回归 |

## 实施前强制动作

1. 修复/刷新 GitNexus 索引并对每个拟改符号重新执行 upstream impact。
2. `GovIndicatorDefinition` 的任何源码或 schema 改动，先向用户再次报告 HIGH 风险和直接消费者清单。
3. API handler 改动补 `api_impact` 与响应 shape 检查。
4. 提交前执行 `gitnexus_detect_changes()`，确认没有生成平行 owner 或波及未计划流程。
5. **前端（TypeScript/React）符号的影响面不得只采信 GitNexus 计数**；使用 GitNexus 流程/上下文定位，并用 scoped `rg` 覆盖相关 `.ts/.tsx/.json`，排除 `node_modules`、`dist` 后交叉验证。依据见上方适用范围说明与 RF-86-04。
6. 关系型变更按 [`data-model-relationships.md`](data-model-relationships.md) 逐 owner 评估：架构字典、计划、ModelSpec/Revision、候选/物化、资产、指标和质量不得只分析单个实体。
