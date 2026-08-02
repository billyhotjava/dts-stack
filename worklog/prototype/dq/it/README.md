# DTS 数据质量正式产品验收

验收时间：2026-08-01（Asia/Shanghai）

## 部署结果

- 正式页面基线：`worklog/prototype/dq`
- 部署服务：`dts-admin:1.0.0`、`dts-platform:1.0.0`、`dts-platform-webapp:1.0.0`
- 服务状态：`dts-admin`、`dts-platform` 健康，`dts-platform-webapp` 正常运行
- 数据库迁移：`20260801_12_quality_run_issue_identity`、`20260801_13_inceptor_quality_templates`、`20260801-04-quality-failing-row-audit-catalog` 已应用
- 回滚镜像：`rollback-dq-20260801-171402`

## 自动化门禁

- dts-platform 数据质量单元测试：133/133 通过
- PostgreSQL Liquibase 集成测试：3/3 通过（含升级、真实回滚和重新应用）
- dts-admin 审计接收测试：15/15 通过
- 前端 TypeScript：通过
- 前端数据质量契约测试：45/45 通过
- 前端生产构建：通过
- 三个正式镜像构建：通过
- `git diff --check`：通过
- GitNexus 最终检测：LOW，0 个受影响执行流

## 真实浏览器验收

- 路由：`#/governance/rules` 和 `#/governance/quality` 均正常加载。
- 页面收敛：质量管控工作台不再包含“质量报告”Tab；旧 `tab=report` 链接自动跳转到独立质量报告菜单页。
- 数据资产：资产选择器包含“未归属业务域”资产，本次选择 `biz_ads_budget_derived_v2`。
- 规则：创建并发布 `DQ-E2E-1785595652796`，列表显示 `v1 / PUBLISHED` 和正确数据资产。
- 运行：运行 `3494f4e2-8985-43c0-882a-04f7494b2d52` 为 `SUCCEEDED`，失败行数为 0，不再出现 Hive/Inceptor 未配置错误。
- 报告：独立质量报告页显示该资产综合质量分 100、COMPLETENESS 100 和规则明细，Excel 导出成功。
- 审计：规则新增、质量运行和报告导出均记录 PENDING/SUCCESS；规则删除记录 SUCCESS。
- 退役接口：`GET /api/governance/data-editor/tables` 返回 404。
- 响应式：390 px 视口下 `scrollWidth = innerWidth = 390`，无横向溢出。
- 浏览器控制台：新认证页签完成主流程时 0 error / 0 warning。
- 清理：验收规则已删除；一次性验收身份的 Keycloak 用户、管理端快照和角色成员均为 0。

截图：[质量报告最终页](evidence/dq-quality-report-final.png)
