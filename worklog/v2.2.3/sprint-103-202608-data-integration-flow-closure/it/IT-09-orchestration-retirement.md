# IT-09：独立任务编排入口退役验收

检查日期：2026-08-29。判定：`PASS_WITH_ENV_NOTE`（源码、部署与本地 Chrome 真实运行态通过；Chrome 95 待现场复验）。

## 验收范围

- 数据集成是当前版本唯一业务入口。
- 独立任务编排菜单、列表、编辑器、画布、运行页及其前端设计接口全部退役。
- 历史地址 `/explore/etl/orchestration` 只兼容跳转到接入概览或指定任务详情。
- 通用 Airflow 实例保留在运维域，并按 `AIRFLOW_DAG` 入口过滤。
- 后端任务版本、准入、调度、执行、质量和资产证据契约保留，不新增第二套 workflow 事实源。

## 自动化证据

- 前端源契约：本次聚焦 11/11 通过，覆盖菜单唯一性、静态/动态路由、兼容跳转、业务/运维入口、新建接入选择器和独立页面物理删除；workspace adapter 3/3 通过。
- Admin Liquibase：定向测试通过；目标菜单可软删除，权限绑定保留，回滚只恢复本 changeSet 处理的记录。
- Platform：在干净临时目录完成 `-DskipTests package`，验证运维入口路由变更可编译。
- Webapp：`LEGACY_BROWSER_BUILD=1` 完整构建通过，包含 TypeScript 检查和 Chrome 95 目标产物。
- 本地浏览器：`/usr/bin/google-chrome` 152.0.7977.64，只读夹具旅程与真实运行态旅程各 1/1 通过。

## 浏览器旅程

只读样例夹具下完成以下检查：

1. 1366×768 打开接入概览，表格、连接管理和统一“新建接入”入口可见；下拉明确提供数据库、API、离线文件三种接入方式。
2. 历史编排地址自动回到 `/foundation/data-sources`。
3. 页面和菜单不再出现“任务编排”。
4. 390×844 重新打开接入概览，标题、筛选、主要操作和关键表格列可用，无空白页。
5. page error、console error、failed request 均为 0。

脱敏截图：`/tmp/sprint103-data-integration-1366x768.png`、`/tmp/sprint103-data-integration-390x844.png`。

## 真实运行态证据

- 发布 revision：`0ec0a9758e071514acce72765dd0922324f99162`；镜像从干净 detached worktree 构建，未混入共享工作区改动。
- 定向替换 `dts-admin`、`dts-platform`、`dts-platform-webapp`；`dts-ingestion` 和 Airflow DAG 未变更。
- Admin changeSet `20260829-01-retire-task-orchestration-menu` 为 `EXECUTED`；`portal_menu.id=1055` 已从 `deleted=false` 收敛为 `deleted=true`。
- Admin、Platform `/management/health` 均为 `UP`；Webapp `nginx -t` 成功，容器内首页和 `https://bi.yuzhicloud.com/` 均返回 200。
- 真实登录 Chrome 旅程 1/1 通过：`/api/menu/tree` 不再返回 `studioOrchestration`，三类新建入口可见，历史编排地址跳回接入概览，page error 为 0。
- 当前运行库无接入任务，因此真实旅程验证的是空状态；带样例行的关键列和窄屏布局由只读夹具旅程覆盖。
- 脱敏截图：`/tmp/sprint103-data-integration-live-1366x768.png`。测试生成的临时认证状态已删除。

当前任务列表与操作接口统一受后端 `INFRA_MAINTAINERS` 约束。本次未虚构无服务端契约的前端只读按钮态；若后续需要浏览者/编辑者分权，应先拆分后端权限契约再实施 UI。

## 环境与发布边界

- 本机没有 Chrome 95，因此本次结果是 `PASS_WITH_ENV_NOTE`，不能替代客户现场 Chrome 95 复验。
- 真实运行态只验证了现有维护者账号；三角色权限隔离仍属于后续权限契约与客户现场验收范围。
