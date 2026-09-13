# F3：集中验收与交付

**优先级**：P0  
**状态**：DONE_WITH_GAPS

## 目标

一次集中证明 API、菜单迁移、门户 UI、发布态运行时、Chrome 95 构建兼容和回滚边界；缺失真实凭据/Chrome95 时保持可审计 GAP。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 聚焦测试、构建与证据归档 | DONE_WITH_GAPS | F1/T01、F2/T01、F2/T02 |

## 完成标准

- [x] Analytics source contract/package、Admin 聚焦 contract 和 webapp legacy build 通过。
- [x] Mock E2E desktop/narrow 无 console/page/network 错误并有截图。
- [x] 未擅自发布客户草稿；真实实例填充态留待部署后由已发布大屏验收。
- [x] release plan、runbook 和工作区范围证据完整；GitNexus staged detect 在 GREEN 提交前执行。
- [ ] Chrome95 实机、隔离 PostgreSQL rollback 与真实发布大屏验收（GAP）。
