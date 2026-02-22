# P2-04 智能报告工厂（Report Factory）

`status`: `done`  
`priority`: `P2`  
`inspiration`: `Hetu(数据报告自动化) + 企业 BI 报告编排能力`

## 目标

基于分析会话与大屏组件，自动生成结构化报告（摘要、图表、结论、风险），并支持定时分发与审计追踪。

## 子任务

1. 报告模板引擎
- 定义 `ReportTemplateSpec`（章节、图表引用、文本块、变量占位）。
- 支持模板版本管理与草稿发布。

2. 自动生成管道
- 输入：会话或大屏 ID。输出：报告草稿（Markdown/HTML）。
- AI 仅生成文案建议，关键数据必须引用真实查询结果。

3. 导出与分发
- 支持 PDF/HTML 导出与邮件/站内分发。
- 支持定时任务（日报/周报/月报）。

4. 报告审计
- 记录报告生成人、分发对象、时间、模板版本、数据快照指纹。
- 提供审计检索与导出接口。

5. 前端报告中心
- 新增模板管理、生成任务、分发记录、下载历史页面。
- 失败任务给出可读原因与重试入口。

## 验收标准

- 从会话生成报告成功率 >= 95%（非权限/网络异常场景）。
- 支持按模板定时生成并分发，失败可重试可追踪。
- 报告内容中图表与数据可回溯至对应会话/查询。

## 风险与回滚

- 风险：AI 文案幻觉导致结论偏差。  
- 回滚：默认启用“数据引用校验”，未通过则降级为模板+人工编辑模式。

## 实现难度评估

- 难度：`高`
- 预计周期：`2.5 ~ 4 周`
- 关键难点：
  - 导出渲染一致性（浏览器、字体、分页）；
  - 任务调度与分发可靠性（幂等、重试、限流）。

## 前置依赖

- `P1-07-selfservice-explore-session.md`
- `P2-02-collaboration-export-mobile.md`
- `P0-04-release-acl-audit-sharing.md`

## 实现记录（2026-02-22）

- 新增报告模板与报告运行数据模型：
  - 数据表：`analytics_report_template`、`analytics_report_run`；
  - 代码：`source/dts-analytics/src/main/resources/config/liquibase/changelog/0033_report_factory.xml`。
- 新增报告工厂 API：
  - 模板管理：`/api/report-factory/templates`（增改删查）；
  - 生成与运行记录：`POST /api/report-factory/generate`、`GET /api/report-factory/runs`、`GET /api/report-factory/runs/{id}`；
  - 导出：`GET /api/report-factory/runs/{id}/export?format=html|markdown`。
  - 代码：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ReportFactoryService.java`、`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ReportFactoryResource.java`。
- 支持以会话/大屏为输入生成结构化报告草稿，并记录分发配置与审计摘要。
- 前端 SDK 已接入报告中心接口。
- 前端报告中心已落地（2026-02-22）：
  - 新增页面：`source/dts-analytics-webapp/modern/src/pages/ReportFactoryPage.tsx`；
  - 新增路由：`/analytics/report-factory`；
  - 新增导航入口：侧边栏“报告工厂”；
  - 能力覆盖：模板创建、运行任务生成、任务详情查看、HTML/Markdown 导出入口。
