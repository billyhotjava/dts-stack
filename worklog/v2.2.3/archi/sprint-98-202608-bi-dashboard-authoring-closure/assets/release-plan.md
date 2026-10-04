# Sprint-98 发布与回滚计划（Gate G3）

## 范围

- 后端：`dts-analytics`，仅 Dashboard 保存预检与响应元数据；无数据库迁移。
- 前端：`dts-platform-webapp`，仅 BI 看板编辑器、目录读取和发布交互。
- 不重建 `dts-platform` Java 服务，不包含共享 SQL Workbench / SQL IDE 改动。

## 发布步骤

1. 记录当前 `dts-analytics`、`dts-platform-webapp` 镜像 ID/标签，确认容器健康。
2. 分别运行后端聚焦测试和前端生产构建，生成新镜像。
3. 仅重建并启动 `dts-analytics`、`dts-platform-webapp`：`docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-analytics dts-platform-webapp`。
4. 检查两个容器为 healthy、代理路由无 5xx，再执行保存草稿、发布范围、旧组件替换、校验、发布与门户消费旅程。

## 失败与回滚

- 保存预检异常：停止发布，保留原容器；该接口在写入前完成批量校验，不需要数据回滚。
- 新容器不健康或页面回归：恢复步骤 1 记录的两个旧镜像标签，再对同一两个服务执行 `up -d --no-deps --force-recreate`。
- 发布业务链失败：不放宽 audience、密级或治理分析门禁；记录 requestId、dashboardId、cardId 和 validate blocker 后回滚镜像。

## 发布后健康信号

- `/bi/api/dashboard/{id}` 返回嵌套分析的类型、生命周期和发布版本。
- `/bi/api/dashboard/save` 对新建/改绑的非治理分析返回 422，未改历史绑定仍可保存并在发布校验中提示替换。
- 组织/角色目录每次编辑器会话各加载一次；页面无 console/page error、无 4xx/5xx 漏报。
- 真实 Chrome 95 不可用时只登记环境缺口，不以 Chrome 150 替代结论。
