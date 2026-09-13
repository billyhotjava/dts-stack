# T04：完成 Chrome 95 与最终 Go/No-Go

**优先级**：P0  
**状态**：READY  
**依赖**：T01、T02、T03

## 目标

在最终候选部署上完成真实用户旅程、浏览器兼容、变更范围审计和分层发布决策。

## 技术设计

- Chrome 95 执行通用画像安装、本地改写、v2 升级冲突、解决、回滚和 PJM 可选包。
- 桌面与 390px 验证无横向溢出、操作可达、失败可恢复。
- 使用真实认证/API/PostgreSQL；mock 只补故障注入，不替代主旅程。
- 运行 `git diff --check`、模块测试/build、GitNexus detect_changes。
- 输出代码、测试、内容、部署、浏览器五层 Go/No-Go。

## 影响范围

- 最终部署容器
- Chrome 95 E2E
- GitNexus 影响报告
- Sprint README/IT 索引

## 验证

- [ ] Journey A-F 全部有真实证据。
- [ ] Critical/Important review issue 清零或明确 NO-GO。
- [ ] 工作区变更只包含预期文件，不覆盖他人修改。

## 完成标准

- [ ] 五层均 GO 才将 Sprint 标记 DONE。
- [ ] 远程内容服务、未授权来源或未来行业包不被误报为已完成。
