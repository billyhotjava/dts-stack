# Sprint-62: 旅程可信化与门禁证据结构化

**时间**: 2026-07
**状态**: IN_PROGRESS
**类型**: UI Productization / Journey Trust / Evidence Engineering
**目标**: 把 sprint-61 建立的旅程从"URL 里的自我声明"升级为"可恢复、可验真、有结构化门禁证据"的可信旅程，让没有正规开发人员的客户中断后能继续、看到的绿色就是真的、验收拿到的是结构化证据而不是一堆链接。

## 背景

Sprint-61 已交付旅程上下文（journeyContext）、阶段状态机（journeyStageState）、上下文条（JourneyContextBar）和验收包模型。对照 DataWorks 类成熟产品、dbt 门禁语义和"客户无正规开发"的现实，存在四个缺口：

1. **旅程无实例**（DataWorks 视角）：旅程状态只活在 URL，刷新丢参、关标签即断链，客户不会手工拼 query。DataWorks 的向导/流程是工作空间内可恢复的持久对象。
2. **完成态是自我声明**（真实性视角）：阶段 done 仅取决于 URL 是否携带 artifact 参数，手改 URL 即可变绿。成熟产品的阶段完成来自对象真实状态。
3. **门禁证据是链接不是结构**（dbt 视角）：dbt build 的体验是 compile→test→materialize 每步有结构化结果；当前 development/evidence 阶段的证据只是页面跳转链接，验收包聚合的也是链接。
4. **菜单直达时旅程不可见**（客户视角）：客户习惯从菜单进页面，无 journey 参数时上下文条不渲染，旅程等于不存在；验收包缺打印友好形态。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 旅程实例持久化与恢复 | 3 | DONE | P0 |
| F2 | 阶段真实性校验 | 3 | IN_PROGRESS | P0 |
| F3 | dbt式门禁证据结构化 | 3 | READY | P0 |
| F4 | 菜单直达旅程感知与验收包打印 | 3 | READY | P1 |

**统计**: READY=7, IN_PROGRESS=0, DONE=5, BLOCKED=0

## 实施顺序

```text
F1 旅程实例持久化（独立，先做，收益最直接）
F2 阶段真实性校验（T01 校验契约 -> T02 状态机接入 -> T03 UI 呈现）
F3 门禁证据结构化（依赖 F2-T02 的状态机扩展点；T03 改造验收包）
F4 菜单直达感知与打印视图（独立，可并行穿插）
```

Browser smoke 依赖 sprint-61 F9 的可登录基线；本 sprint 不重复建设，验证以 source-contract + build 为主，浏览器证据挂靠 F9。

## 产品原则（承接 sprint-61 并追加）

- 旅程中断必须可恢复：客户关掉浏览器再回来，工作台要能"继续上次旅程"。
- 绿色必须可信：无法验真的阶段宁可显示"待确认"，不显示"已就绪"。
- 证据是结构不是链接：每项门禁给出 ready/missing/blocked 与详情，缺 API 就标注 apiName。
- 所有新增状态均覆盖 default/loading/empty/disabled/error/success/permission。
- 不伪装后端能力：持久化先落 localStorage，后端旅程实例 API 作为显式缺口标注。

## 完成标准

- [ ] 刷新或重开浏览器后，工作台出现"继续上次旅程"入口，恢复后 8 阶段状态与中断前一致。
- [ ] 携带无效 artifact id 进入旅程时，对应阶段显示 blocked 与恢复动作，不显示 done。
- [ ] development/evidence 阶段展示结构化门禁卡（落标覆盖率/编译/测试/运行四项），验收包按 checks 聚合并标注缺失 API。
- [ ] 从菜单直达旅程相关页面时，出现可关闭的"加入旅程"提示，进入后上下文完整。
- [ ] 验收包提供打印友好视图（Chrome 95 可用）。
- [ ] 全部新模块有 source-contract 测试；`node --test` 与 `pnpm build` 通过；GitNexus detect_changes 无预期外影响。

## 非目标

- 不做后端旅程实例存储 API（只留契约与缺口标注）。
- 不做多旅程类型/多实例并行管理（当前仅 e2e-data-product 单实例）。
- 不重写 sprint-61 已交付组件的接口，只做增量扩展。
- 不在本 sprint 解除登录/DNS 阻断（归 sprint-61 F9）。

## 关键文件

- `source/dts-platform-webapp/src/components/journey/journeyContext.ts`
- `source/dts-platform-webapp/src/components/journey/journeyStageState.ts`
- `source/dts-platform-webapp/src/components/journey/JourneyContextBar.tsx`
- `source/dts-platform-webapp/src/components/journey/dataProductAcceptancePackage.ts`
- `source/dts-platform-webapp/src/pages/workbench/DataManagementWorkbenchPage.tsx`

## 资产

- 缺口分析与对标结论：`assets/gap-analysis.md`
- 集成验证计划：`it/README.md`
