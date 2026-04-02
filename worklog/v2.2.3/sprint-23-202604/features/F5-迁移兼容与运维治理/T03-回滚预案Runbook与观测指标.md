# T03: 回滚预案、Runbook 与观测指标

**优先级**: P1
**状态**: READY
**依赖**: T01,T02

## 目标
让运维和值班人员在发布窗口内能够快速判断是否继续推进或触发回滚。

## 技术设计
- 设计核心观测指标: 登录成功率、`session/current` 成功率、`/bi/api/screens/*` 401 比例、redirect 次数、takeover 事件数
- 规定平台、代理、analytics 三侧日志关键字和排查顺序
- 形成发布前、发布中、发布后的检查单和回滚步骤
- 明确触发回滚的阈值与决策责任人
- 在 `assets/` 中沉淀日志模板、SQL、curl、浏览器检查脚本

- 运行手册以 `assets/release-runbook.md` 为准，值班同学按文档直接执行

## 影响范围
- `services/dts-proxy/`
- `source/dts-platform/src/main/resources/logback-spring.xml`
- `source/dts-analytics/src/main/resources/`
- `worklog/v2.2.3/sprint-23-202604/assets/`

## 验证
- [ ] 演练一次完整的发布检查和回滚流程
- [ ] 指标和日志能在 5 分钟内定位会话异常所在层
- [ ] Runbook 可由非开发值班同学执行

## 完成标准
- [ ] 回滚步骤、阈值、责任边界明确
- [ ] 发布窗口具备可操作的观测和决策材料
- [ ] 非开发同学可独立完成首轮排障
