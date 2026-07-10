# Sprint-62 集成验证计划

## Source Contract（node --test）

- [x] `JourneySnapshot.source-contract.test.ts`：快照写入/读取/版本不匹配丢弃/清除；恢复 URL 与中断前参数一致。
- [x] `JourneyArtifactValidation.source-contract.test.ts`：三态契约、七参数 API 缺口标注、注入式草稿清单、异常不抛出。
- [x] `JourneyStageState` 扩展用例：invalid→blocked+恢复动作、valid→done+verified、unknown→done+unverified、复数解析器透传、不传 validations 行为不变。
- [x] `GateEvidence.source-contract.test.ts`：四项 checks 结构、API 缺口标注、校验联动、barrel 导出。
- [x] `DataProductAcceptancePackage` 扩展用例：门禁组前置、verdict→状态映射、invalid→blocked、markdown/json 门禁明细、原九组保留。
- [x] `JourneyContextBar` 扩展用例：joinable 提示、进入/关闭动作、sessionStorage 记忆、模式判定纯函数四态。
- [ ] 路由一致性用例：STAGE_CONFIG 全部 route 在应用路由表中存在。

## 命令

- [ ] `cd source/dts-platform-webapp && node --test --experimental-strip-types "src/components/journey/*.source-contract.test.ts"`（以仓库现行 test 命令为准）
- [ ] `cd source/dts-platform-webapp && pnpm build`
- [ ] `git diff --check`
- [ ] GitNexus `detect_changes`

## Browser Smoke（挂靠 sprint-61 F9 可登录基线）

- [~] `/workbench` 无参数：恢复卡已实现（journey-resume-card/continue/clear testid 就绪），浏览器截图待 sprint-61 F9 可登录基线。
- [ ] `/workbench?journey=e2e-data-product&modelId=<无效id>`：modeling 阶段 blocked 且有恢复动作。
- [ ] `/studio/sql-modeling?journey=...`：门禁摘要卡渲染。
- [ ] `/foundation/data-sources`（无 journey）：joinable 提示出现且可关闭。
- [ ] 验收包打印视图 Chrome 95 截图。

## Chrome 95 风险

- [ ] 打印样式只用 print media query 与基础 CSS，不用现代打印 API。
- [ ] localStorage 异常（隐私模式/配额）需静默降级，不阻塞页面。
- [ ] 门禁卡窄屏 1366x768 不遮挡原页面按钮。

## 证据存放

各 Feature 完成后在本文件追加 GREEN/RED 记录与命令输出摘要；浏览器截图归入 sprint-61 F9 基线目录并在此登记链接。

## 证据记录

### F1-T01 旅程快照存储模块（2026-07-10）

- GREEN：`pnpm vitest run src/components/journey/journeySnapshot.test.ts` 7/7 通过（round-trip、版本不匹配清除、未知 journey/stage 丢弃、坏 JSON 清除、storage 抛异常静默、无 storage 降级、resume URL 含 journey 与全部参数）。
- GREEN：`node --test src/components/journey/JourneySnapshot.source-contract.test.ts` 4/4 通过（导出契约、可注入 storage、API 缺口标注、barrel 导出）。
- GREEN：`pnpm build`（tsc + vite legacy）通过。
- GitNexus detect_changes：本任务仅新增 journeySnapshot 模块与 index.ts 追加导出；检出的 medium 风险项均属 sprint 外既有未提交改动（API 数据源连通性），与本任务无交集。

### F1-T02 上下文变化自动保存快照（2026-07-10）

- GREEN：`pnpm vitest run src/components/journey/journeySnapshot.test.ts` 10/10（新增：enabled 才持久化、同上下文去重、stage/参数漂移判定六组合）。
- GREEN：`node --test src/components/journey/*.source-contract.test.ts` 全套 0 fail（新增 hook 自动持久化契约用例）。
- GREEN：`pnpm build` 通过。
- 影响面：gitnexus impact 对 useDataProductJourneyContext 无索引记录（新文件晚于索引），grep 确认唯一消费方为 JourneyContextBar，纯增量 effect，向后兼容。

### F1-T03 工作台继续上次旅程入口（2026-07-10）

- GREEN：`pnpm vitest run .../journeySnapshot.test.ts` 13/13（新增 shouldOfferSnapshotResume 四态、savedAgo 时间分桶、describeJourneySnapshot 阶段标题+带标签上下文摘要）。
- GREEN：`node --test` journey 契约套件 + workbench 契约 0 fail（新增 workbench 恢复卡契约：loadJourneySnapshot/resume testid/清除动作）。
- GREEN：`pnpm build` 通过。
- F1 整体 DONE：快照存储 → 自动保存 → 工作台恢复三环闭合；旅程中断可恢复目标达成（浏览器实测证据挂靠 F9）。

### F2-T01 artifact 校验契约与数据源（2026-07-10）

- GREEN：`pnpm vitest run .../journeyArtifactValidation.test.ts` 5/5（本地草稿验真 valid/invalid/UUID→unknown、无校验源→unknown、reason/apiName 附着规则、valid 无缺口标记、校验器抛错降级 unknown）。
- GREEN：`node --test` journey 契约套件 0 fail。
- GREEN：`pnpm build` 通过。
- 设计说明：standardDraftId 用注入式 StandardDraftLookup 对接 `standardBindingDraft.ts` 的本地草稿清单（页面接线在 T03，避免 components→pages 反向依赖）；其余六类参数如实返回 unknown 并标注待补 API（ARTIFACT_VALIDATION_API_NAMES）。

### F2-T02 阶段状态机接入校验结果（2026-07-10）

- GREEN：`pnpm vitest run` journey 三模块 23/23（新增 journeyStageState.test.ts 5 用例：兼容性/invalid 阻断/verified/unverified/复数透传）。
- GREEN：`node --test` 契约套件 0 fail——**既有用例零破坏**，第三参可选、默认行为与 sprint-61 基线一致。
- GREEN：`pnpm build` 通过。
- 设计说明：新增 verification 字段（verified/invalid/unverified）与 status 解耦——invalid 改变 status（blocked+恢复动作），unknown 不改变 status 只标记"待确认"，供 T03 在 UI 上区分"纯绿"与"绿但未验真"。

### F2-T03 工作台与上下文条呈现校验状态（2026-07-10）

- GREEN：`pnpm vitest run src/components/journey/` 25/25（新增 journeyContext.test.ts：参数提取、清参 URL 保 journey）。
- GREEN：`node --test` 契约 0 fail（新增 Bar 校验呈现契约、workbench/LowCode 真实校验源接线契约）。
- GREEN：`pnpm build` 通过。
- 交付说明：workbench 阶段卡 done+unverified 显示"待确认"金标、invalid 阶段给"清除无效参数"按钮（保 journey 清单参）；JourneyContextBar 新增可选 validations 注入，invalid 上下文红标可关闭清除；LowCodeDevelopmentPage 作为 Bar 校验接线样板（真实 standardBindingDraft 清单）。F2 整体 DONE：手改 URL 塞假 standardDraftId 时对应阶段 blocked，不再显示纯绿。

### F3-T01 门禁证据数据模型（2026-07-10）

- GREEN：`pnpm vitest run .../gateEvidence.test.ts` 5/5（verdict 优先级 blocked>missing>ready、参数推导四项 check、证据 URL 保 journey 上下文、invalid artifact→blocked→fail、非 ready 项 apiName 规则）。
- GREEN：`node --test` 契约套件 0 fail；`pnpm build` 通过。
- 设计说明：四项 check 对齐 dbt build 语义（落标约束/编译/测试/运行）；test 项接口未接入前恒 missing 并标注 `GET /api/dbt/test-results`，不伪装；与 F2 校验联动——invalid 对象使对应 check blocked、verdict fail。

### F3-T02 门禁摘要卡渲染（2026-07-10）

- GREEN：vitest 行为用例 30/30（按行为测试文件运行；vitest 目录模式会误收 node:test 契约文件，契约统一由 node --test 承担）。
- GREEN：`node --test` 契约 30/30（新增摘要组件与三页接入契约）。
- GREEN：`pnpm build` 通过（修复一处未使用导入的 tsc 报错）。
- 交付说明：GateEvidenceSummary（verdict 徽标+四 check 标签，点击跳证据页保 journey 上下文，title 提示待补 API）；workbench development/evidence 阶段卡内嵌 compact 摘要（联动校验结果）；SqlModelingPage/OpsInstancesPage 经 JourneyGateEvidenceSummary 一行接入（仅旅程模式渲染）。

### F3-T03 验收包接入结构化门禁（2026-07-10）

- GREEN：vitest 9/9（验收包 4 + 门禁 5）；`node --test` 契约 31/31；`pnpm build` 通过。
- 交付说明：验收包新增"发布门禁"组（verdict pass→ready/warn→missing/fail→blocked，缺项明细进 missingReason）；包级新增 gateEvidence 结构字段；markdown 导出含门禁明细表（状态/说明/证据或待补接口）；workbench 传入 F2 校验结果，验收包与门禁与校验三方联动。F3 整体 DONE。

### F4-T01 加入旅程提示（2026-07-10）

- GREEN：vitest journeyContext 4/4（新增 resolveJourneyBarMode 四态、关闭记忆按 stage 隔离）；`node --test` 契约 32/32；`pnpm build` 通过。
- 交付说明：JourneyContextBar 三模式（journey/joinable/hidden）——菜单直达时显示单行浅底提示"此页面是数据产品旅程的第 N 步"+进入旅程/关闭；关闭记忆入 sessionStorage（按 stage 隔离，storage 异常静默降级）；journey 模式行为零变化。8 个已接入页面自动获得该能力，无需改页面。

### F4-T02 验收包打印友好视图（2026-07-10）

- GREEN：vitest 6/6（新增打印元信息：上下文摘要/时间戳/三签字栏、空上下文降级文案）；`node --test` 契约 33/33；`pnpm build` 通过。
- 交付说明：验收包卡片标记 data-print-root，"打印视图"按钮直接 window.print()；@media print 用 visibility 方案隐藏应用壳（Chrome 95 兼容，无现代打印 API）、按钮不打印、分组卡防跨页断裂；打印头（标题/摘要/上下文/生成时间）与签字栏（数据管理岗/业务验收人/技术支持方）为 print-only 元素。Chrome 95 打印预览截图挂靠 F9。
