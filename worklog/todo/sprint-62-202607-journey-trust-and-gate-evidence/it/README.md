# Sprint-62 集成验证计划

## Source Contract（node --test）

- [ ] `JourneySnapshot.source-contract.test.ts`：快照写入/读取/版本不匹配丢弃/清除；恢复 URL 与中断前参数一致。
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

- [ ] `/workbench` 无参数：出现"继续上次旅程"卡（有快照时）。
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
