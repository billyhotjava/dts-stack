# Chrome 95 兼容构建与浏览器 smoke

生产构建启用 `LEGACY_BROWSER_BUILD=1`，14 条定向源码契约、TypeScript 和 Vite build 均通过。

真实页面结果：

- `/catalog/assets/ledger`：台账总量 487，本页 10，49 页；有效密级、待封存、来源缺口和主题域状态可见。
- legacy 治理详情：dataset API 200，不再先请求不存在的 assets-v2 详情；业务标签空态正常，控制台 0 错误。
- 密级事实：来源声明、识别结果、人工下限、有效密级、传播状态、快照版本和字段密级可见。
- 生命周期工作台：workspace、metrics、issues 均 200；管理员旁路修正后控制台 0 错误。
- 390×844：主题域侧栏折叠，主区宽度约 341px；工作台为 390×844 全屏 Drawer。
- `/bi/screens`：大屏“有效密级”列说明取全部展示数据的最高密级，并展示人工下限；API 200，控制台 0 错误。

截图位于本目录：

- `sprint72-asset-ledger-1366.png`
- `sprint72-governance-detail-1366.png`
- `sprint72-classification-fact-1366.png`
- `sprint72-lifecycle-workbench-1366.png`
- `sprint72-migration-dry-run-1366.png`
- `sprint72-asset-ledger-390-fixed.png`
- `sprint72-lifecycle-workbench-390.png`
- `sprint72-screens-effective-classification-1366.png`

尚未完成接入向导和大屏编辑/发布/公开访问的 Chrome 95 全旅程，因此证据域结论仍为 `PARTIAL`。
本轮新增的生命周期六阶段时间轴、销毁证明详情、资产详情人工密级下限和审批入口尚未进入现网
容器，需随 Sprint 统一发布后补充 1366×768 与窄屏真实页面 smoke。
