# T01: 三模块现状审计与 API 缺口登记

**优先级**: P0
**状态**: IN_PROGRESS（静态审计完成，运行时验证待容器重建）
**依赖**: 无

## 静态审计结论（2026-07-03）

三模块**均非纯占位**：前端接真实 platformApi，后端 REST + 服务栈存在。"功能不完善"的疑点在**执行深度与动线闭环**，需运行时验证定位。

### 质量管控 /governance/rules（QualityRulesPage 922 行）

| 层 | 现状 |
|----|------|
| 前端 API | 11 个函数：rules CRUD、versions、version-status、toggle、dry-run、run、templates、previewTemplateSQL |
| 后端端点 | GovernanceResource 全部对应存在（L172-631），含 runs/failing-rows |
| 执行栈 | QualityRunService / QualityStatementExecutor / QualityTaskScheduler / IngestionQualityBridge 均在 |

**待运行时验证**：① 试跑是否真执行 SQL 并回写 run/history；② 模板预览 SQL 是否可用；③ 规则→数据集绑定动线是否走得通。

### 质量报告 /governance/quality（QualityReportTab 411 行）

| 层 | 现状 |
|----|------|
| 前端 API | getQualityScore / getRuleHistory / listDatasets / listQualityRules |
| 后端端点 | /quality/score、/quality/dashboard、/quality/rules/{id}/history 存在；QualityScoreService/QualityDashboardService 在 |

**待运行时验证**：score/dashboard 是否基于真实 run 数据聚合（疑似空库下全 0，被感知为占位）；与管控页互跳缺失。

### 分级分类 /security/data-security（data-security.tsx 582 行）

| 层 | 现状 |
|----|------|
| 前端 API | 10 个函数：classification-mapping（get/replace/validate/masking-linkage）、masking-rules CRUD、dataset security-mapping |
| 后端端点 | 挂在 **/catalog/** 命名空间（AssetResource 等），非 governance；已确认存在 |

**待运行时验证**：① 批量定级能力是否存在（目前疑似仅单数据集映射）；② 与资产台账密级列的联动（SECURITY_LINKAGE_EVENT 机制）是否生效。

## 下一步（T02-T04 进场条件）

1. **依赖容器重建**：dts-platform + webapp 重建后逐条跑上述验证点，把"疑似"变"确认"。
2. 每模块确认缺口后按 TDD 补齐：先写 source-contract/单测锁定目标动线（RED）→ 实现（GREEN）。
3. 验证记录追加到本文档，缺口清单落到各任务文档。

## 审计修正（2026-07-03 循环迭代）

1. **"与管控页互跳缺失"为伪缺口**：`/governance/rules`（QualityRulesPage）本身是五 Tab 容器（概览/规则/任务/报告/修复），已内嵌 QualityReportTab；独立菜单"质量报告"与之复用同一组件，无需额外互跳。
2. **确认并修复一个真实占位缺口**：质量报告 run 历史中 FAILED 行的"去修复"Typography.Link 无 onClick（死链接），已修复为跳转 `/governance/rules?tab=repair&runId=...`（activeTab 由 URL tab 参数驱动，已核实落点正确）。TDD：qualityReportRepairLink.source-contract.test.ts 先 RED 后 GREEN。

## 运行时补证（2026-07-04）

### 质量管控 /governance/rules 与质量报告 /governance/quality

- 已修复一个真实动线断点：质量规则 `执行` 成功后不再只停留 toast，而是切到 `质量报告` 页签并携带 `datasetId`，形成“规则执行 -> 查看报告”的前端闭环。
- 质量报告页已支持 `datasetId` URL 参数：`/governance/rules?tab=report&datasetId={datasetId}` 可激活 `质量报告` 并保留数据集选择；默认数据湖接口失败时不丢失 URL 数据集上下文。
- TDD：新增 `QualityRulesReportFlow.source-contract.test.ts`，先 RED 后 GREEN；`pnpm exec tsc --noEmit` 通过。
- Playwright smoke：`quality-smoke-001` 深链可渲染 `质量报告`、保留数据集选择并显示 `导出报告`，截图 `assets/it-15-f5-quality-report-deeplink.png`。
- 仍待运行时验证：当前本地质量评分/规则历史接口返回服务器错误或空数据，尚未证明 `triggerQualityRun` 真执行 SQL、回写 run/history，并驱动 score/dashboard 聚合。

### 分级分类 /security/data-security

- 已修复一个真实动线断点：资产台账行级新增 `分级分类`，可直接进入 `/security/data-security?tab=datasetSecurity&datasetId={assetId}`。
- 分级分类页已支持 `tab/datasetId` URL 参数：深链可激活 `数据集安全字段` 页签并带入数据集选择；顶部 `绑定资产` 不再是 disabled 占位按钮。
- 分类映射页签已新增 `批量导入` / `导出映射`：CSV 导入使用 `importClassificationMapping`，导出使用 `exportClassificationMapping` 并下载 CSV，补齐批量维护入口。
- TDD：`F5DataSecurityLinkage.source-contract.test.ts` 先 RED 后 GREEN；相关回归 9/9 通过，`pnpm exec tsc --noEmit` 通过。
- Playwright smoke：`asset-smoke-001` 深链可渲染目标页签与选择框，截图 `assets/it-13-f5-data-security-deeplink.png`。
- Playwright smoke：分类映射页签可见 `批量导入`、`导出映射`、`保存映射`，截图 `assets/it-14-f5-classification-batch.png`。
- 仍待运行时验证：本地 Vite 代理下 `/api/catalog/classification-mapping`、`/api/catalog/masking-rules`、`/api/catalog/datasets/{id}/security-mapping`、`/api/catalog/classification-masking/linkage` 返回 500；真实密级保存、脱敏联动回写与批量定级需后端可用后继续补证。
