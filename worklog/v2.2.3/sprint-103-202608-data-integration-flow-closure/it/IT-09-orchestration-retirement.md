# IT-09：独立任务编排入口退役验收

检查日期：2026-08-29。判定：`PASS_WITH_ENV_NOTE`（源码通过，部署待执行）。

## 验收范围

- 数据集成是当前版本唯一业务入口。
- 独立任务编排菜单、列表、编辑器、画布、运行页及其前端设计接口全部退役。
- 历史地址 `/explore/etl/orchestration` 只兼容跳转到接入概览或指定任务详情。
- 通用 Airflow 实例保留在运维域，并按 `AIRFLOW_DAG` 入口过滤。
- 后端任务版本、准入、调度、执行、质量和资产证据契约保留，不新增第二套 workflow 事实源。

## 自动化证据

- 前端源契约：13/13 通过，覆盖菜单唯一性、静态/动态路由、兼容跳转、业务/运维入口和独立页面物理删除。
- Admin Liquibase：定向测试通过；目标菜单可软删除，权限绑定保留，回滚只恢复本 changeSet 处理的记录。
- Platform：在干净临时目录完成 `-DskipTests package`，验证运维入口路由变更可编译。
- Webapp：`LEGACY_BROWSER_BUILD=1` 完整构建通过，包含 TypeScript 检查和 Chrome 95 目标产物。
- 本地浏览器：`/usr/bin/google-chrome` 152.0.7977.64，Playwright 定向旅程 1/1 通过，用时 6.0 秒。

## 浏览器旅程

只读空数据夹具下完成以下检查：

1. 1366×768 打开接入概览，表格、连接管理和新建接入入口可见。
2. 历史编排地址自动回到 `/foundation/data-sources`。
3. 页面和菜单不再出现“任务编排”。
4. 390×844 重新打开接入概览，标题、筛选、主要操作和空状态可用，无空白页。
5. page error、console error、failed request 均为 0。

脱敏截图：`/tmp/sprint103-data-integration-1366x768.png`、`/tmp/sprint103-data-integration-390x844.png`。

## 环境与发布边界

- 本机没有 Chrome 95，因此本次结果是 `PASS_WITH_ENV_NOTE`，不能替代客户现场 Chrome 95 复验。
- 本地 platform 后端未监听 18082，开发代理域名在主机内不可解析；浏览器使用显式 `E2E_FIXTURE_ONLY=1` 的只读夹具模式。默认 E2E 真实登录流程未改变。
- 本次没有构建或替换运行镜像，也没有在运行数据库执行菜单迁移；部署后仍需核对菜单记录、角色可见性、兼容地址和服务健康。
