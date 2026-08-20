# F3：集中验收与交付

**优先级**：P0  
**状态**：DRAFT（等待 F1/F2）

## 目标

一次集中证明 API、菜单迁移、门户 UI、发布态运行时、Chrome 95 构建兼容和回滚边界；缺失真实凭据/Chrome95 时保持可审计 GAP。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 聚焦测试、构建、部署验收与证据归档 | DRAFT | F1/T01、F2/T01、F2/T02 |

## 完成标准

- [ ] Analytics/Admin 聚焦测试和 webapp legacy build 通过。
- [ ] Mock E2E desktop/narrow 无 console/page/network 错误并有截图。
- [ ] 运行实例空态 smoke 与健康检查通过；不擅自发布客户草稿。
- [ ] release plan、runbook、GitNexus detect 和工作区范围证据完整。
