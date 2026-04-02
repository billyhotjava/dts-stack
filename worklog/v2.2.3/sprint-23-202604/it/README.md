# Sprint-23 集成验证

**状态**: READY

## 验证策略

本 Sprint 聚焦真实会话链路重构，验证必须覆盖浏览器、平台后端、代理和 analytics 四层联动:

- 同一浏览器双 tab 同时打开 `platform-webapp` 和大屏预览，至少运行 30 分钟，无 401 风暴、闪屏、慢刷、错误“异地登录”
- 新浏览器或第二台机器登录同账号时，旧浏览器整组 session 被明确接管，提示语义一致
- 登录、续租、登出、idle 超时、absolute 超时、takeover 都能映射到唯一的服务端 reason code
- analytics 的 screen、dashboard、card 接口只通过 forward-auth 获取身份，不再接受 bearer fallback
- 发布切换和回滚在灰度窗口内可执行，观测指标可证明行为变化

## 验证清单
- [ ] 会话矩阵覆盖同浏览器多 tab、跨浏览器、跨机器三类核心路径
- [ ] `portal_sessions` 数据迁移在测试环境完成正向和回滚演练
- [ ] `session/current`、登录、登出、续租接口具备自动化或半自动化验证脚本
- [ ] 大屏预览场景完成至少一轮 30 分钟稳定性测试
- [ ] Traefik、platform、analytics 日志中能关联同一浏览器会话链路
- [ ] 关键异常告警和审计事件已接入运维 Runbook

## 证据存储
- 验证记录: `../assets/verification-*.md`
- 浏览器抓包: `../assets/network-*.har`
- 日志摘录: `../assets/logs-*.md`
- 发布和回滚记录: `../assets/release-*.md`

## 手工验证入口
- 会话矩阵与稳定性验证: `../assets/manual-verification-checklist.md`
- 发布窗口与回滚执行: `../assets/release-runbook.md`
