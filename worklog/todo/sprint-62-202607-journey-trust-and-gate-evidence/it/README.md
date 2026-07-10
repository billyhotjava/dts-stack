# Sprint-62 集成验证计划

## Source Contract（node --test）

- [x] `JourneySnapshot.source-contract.test.ts`：快照写入/读取/版本不匹配丢弃/清除；恢复 URL 与中断前参数一致。
- [ ] `JourneyArtifactValidation.source-contract.test.ts`：有效 id、无效 id、未提供 id、校验器缺失四种输入的状态输出。
- [ ] `JourneyStageState` 扩展用例：注入 invalid 校验结果后 status=blocked、恢复动作存在、不出现 done。
- [ ] `GateEvidence.source-contract.test.ts`：四项 checks 的 ready/missing/blocked 组合与聚合 verdict；缺 API 时 apiName 必填。
- [ ] `DataProductAcceptancePackage` 扩展用例：按 checks 聚合、missing 项计数、markdown 导出含门禁明细。
- [ ] `JourneyContextBar` 扩展用例：joinable 模式渲染、关闭后会话内不再出现、journey 模式下不显示 joinable。
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
