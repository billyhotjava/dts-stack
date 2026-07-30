# 发布安全计划（Gate G3）

**变更类型**：能力替换（前端建模旅程收敛）
**风险等级**：中（主建模入口变化；无数据库、数据、后端 API 或权限契约变化）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|---|---|---:|---|
| Expand | 新工作台 Shell、URL 子面板状态、canonical owner 嵌入适配 | 是 | 回切前端旧镜像 |
| Migrate | 旧深链继续保留；活动菜单暂不删除 | 是 | 无数据迁移 |
| Contract | 删除已确认无引用的过渡 Landing；客户旧路由/旧表删除 | 仅前者；后两者否 | Git 提交和旧镜像均可恢复 |

本次不包含 schema、表数据、Liquibase、API 请求/响应或服务间契约变更。

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|---|---|---:|---|
| `/modeling/workbench` 用户 | `ModelingWorkbenchPage.source-contract.test.ts` | 是 | URL 保留 `planId`，新增可选 `module/workspaceView` |
| 建设规划深链 | `WarehousePlanDetailPage.source-contract.test.ts` | 否 | 原 wildcard 路由与编辑/CAS 逻辑保留 |
| 数据元深链 | `ElementsPage.source-contract.test.ts` | 否 | 默认页面布局保留，仅增加可选 `embedded` |
| 模型、维度、指标、关系图 | `ModelingWorkspacePanels.source-contract.test.ts` | 否 | 工作台懒加载既有 owner，不复制 API |
| 旧建模兼容路由 | `modelingRouteConvergence.source-contract.test.ts` | 否 | 继续进入统一 compatibility page |

## 3. 回填策略

- 不适用：本次无数据结构和存量数据变更。
- 工作台 URL 新字段均为可选；未知值降级到模块默认视图。

## 4. 回滚

- 实际运行回滚基线：`dts-platform-webapp:rollback-sprint79-f1t02` → `sha256:811ab4777cec9eac355812c6ccecc97d40350521f5f63457eba303aa67ca171c`。
- 最终发布镜像：`dts-platform-webapp:sprint79-f1t02` → `sha256:7b939a81115eeda33c7e37800cf65e9ae3a507eaae0b9f228e9cf30a77130403`。
- 回滚命令：

```bash
docker tag dts-platform-webapp:rollback-sprint79-f1t02 dts-platform-webapp:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp
```

- 演练方式：新镜像首次验证后实际回切旧镜像，确认入口响应，再重新部署新镜像。
- 演练结果：2026-07-30 20:16 CST，旧镜像回切后容器镜像 ID 精确匹配且 HTTPS 200；20:16:58 恢复最终镜像，镜像 ID 精确匹配且 HTTPS 200，**PASS**。
- 不可逆部分：无；不写数据、不发外部通知、不删除客户对象。

### F2/T01 单页模型编辑器发布演练

- 精确源码提交：`aacf2d660`。
- 部署前运行镜像：`dts-platform-webapp:predeploy-sprint79-f2t01` → `sha256:b773c552d5efc2177bbf6f25ef284b8209bb610bb21b9d22652fc4e8674a258f`。
- F2 发布镜像：`dts-platform-webapp:sprint79-f2t01-aacf2d660` → 同一镜像 ID；精确提交相对部署前产物只包含测试契约调整和空行整理，生产 bundle 字节级复用。
- 回滚目标：`dts-platform-webapp:sprint79-f1t02` → `sha256:7b939a81115eeda33c7e37800cf65e9ae3a507eaae0b9f228e9cf30a77130403`。
- 演练结果：2026-07-30 21:06 CST 实际切回 F1 后 HTTPS 200；随后恢复 F2，运行镜像精确匹配 `sha256:b773c…` 且 HTTPS 200，**PASS**。
- 恢复后认证工作台回归：Playwright 1/1 通过；一次性 Keycloak 用户、管理员快照和认证状态文件均清理为 0。

### F2/T04 模型对象上下文候选验收

- 精确源码提交：`559fef0e5`。
- 隔离构建目录只包含该提交，未包含当前工作树的并行分析看板改动。
- 候选镜像：`dts-platform-webapp:sprint79-f2t04-559fef0e5` →
  `sha256:3303dfb0ef122614a08a6293530e6b0848111b46b4b426ec95e7aff5801caaee`。
- 临时部署前镜像及回滚标签：
  `dts-platform-webapp:predeploy-sprint79-f2t04-concurrent` →
  `sha256:6ad5170b1c5ca5665061f00121881f908a58379d52b2db51942c4a8969854b19`。
- 候选认证验收：严格只读 Playwright 2/2，通过单页逻辑画布和工作台对象恢复旅程；
  建模写请求 0、建模 API 异常响应 0、临时身份清理 0/0/absent。
- 恢复结果：2026-07-30 22:13 CST 恢复并行会话原镜像，容器 image ID 精确匹配
  `sha256:6ad5170b…`，状态 running，HTTPS 200，**PASS**。
- 当前运行态不是候选镜像；候选已保存，待与后续 F3/F4 主线切片一并走正式发布门禁。

## 5. 部署顺序与影响面

1. 保存当前运行镜像的回滚标签。
2. 构建 `dts-platform-webapp:1.0.0`，另存新版本标签。
3. 仅重建 `dts-platform-webapp`。
4. 检查容器、HTTPS 入口和认证工作台。
5. 执行一次旧镜像回切演练，再恢复新镜像并完成浏览器验收。

影响面仅为 `dts-platform-webapp` 的建模页面；`dts-platform`、`dts-admin`、数据库、dbt 和 Airflow 不重启。

## 6. 运行态观测

- 2026-07-30 20:12 CST 验收期间，前端容器曾被并行外部操作置为 `Created`，登录路由短暂返回 404。
- 本轮通过 `docker compose ... up -d --no-deps dts-platform-webapp` 恢复同一最终镜像后，登录路由返回业务 400、认证测试恢复正常。
- 该漂移不来自验收脚本；最终状态再次核对为 `sha256:7b939...`、`running`。
- F2/T01 恢复后最终状态再次核对为 `sha256:b773c...`、`running`；后端、数据库、dbt 和 Airflow 未重启。
