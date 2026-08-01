# 数据集成接入工作台最终聚焦 E2E

**执行时间**：2026-08-01T07:21:00+08:00  
**环境**：本机 `v223` Docker 部署，正式入口 `https://bi.yuzhicloud.com`  
**结论**：API 驱动 E2E `PASS`；UI 点击验收 `BLOCKED`（测试账号登录 HTTP `401`）

## 线上读链路

| 验证项 | 结果 | 证据 |
|---|---|---|
| Ingestion 管理健康 | PASS | `/management/health` 返回 `200 / UP` |
| Platform pairwise token | PASS | 任务列表、连接器能力、默认策略均返回 `200` |
| 错误 token | PASS | 任务列表返回 `403` |
| Airflow 最小权限 | PASS | Airflow token 访问 Platform 任务列表返回 `403` |
| 接入目录 | PASS | 共 `23` 个任务：数据库 `5`、API `2`、离线文件 `16` |
| 数据库代表任务 | PASS | 任务 `18`：详情 `200`、修订 `200`（2）、执行记录 `200`（20） |
| API 代表任务 | PASS | 任务 `16`：详情 `200`、修订 `200`（2）、执行记录 `200`（0） |
| 文件代表任务 | PASS | 任务 `26`：详情 `200`、修订 `200`（2）、执行记录 `200`（1） |
| 响应凭据检查 | PASS | 三类代表任务详情中密码、密钥、token 标量计数均为 `0` |

## UI 路由与运行状态

- `/foundation/data-sources`
- `/foundation/data-sources/database`
- `/foundation/data-sources/api`
- `/foundation/data-sources/files`
- `/foundation/data-sources/defaults`

以上正式入口均返回 HTTP `200`。Admin、Platform、Ingestion、Analytics、Airflow Webserver 健康；Platform Webapp、Airflow Scheduler、Triggerer 正常运行；目标容器重启数均为 `0`。

## 验收边界

- 本次 E2E 使用只读线上 API，验证已部署的三类接入目录、任务详情、修订、执行记录、服务鉴权和路由可达性；未创建、执行、回滚或删除业务任务。
- 仓库默认测试账号调用 `/api/keycloak/auth/login` 仍返回 HTTP `401`，因此没有伪造 UI 登录或把 API 结果写成 UI 点击通过。账号恢复后需由人工补做创建/编辑/准入/执行页面验收。
- 第一次探针误用了不存在的 `/actuator/health`，经容器 Healthcheck 确认实际管理端点为 `/management/health`；修正后完整验收通过，该探针错误不是服务故障。
- 存量任务 `18` 的 nullable `fallback` NPE 是独立遗留问题，失败已终止且未形成无限重试，本次没有越界修改。
