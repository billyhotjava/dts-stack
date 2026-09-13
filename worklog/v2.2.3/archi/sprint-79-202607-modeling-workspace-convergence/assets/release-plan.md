# 发布安全计划（Gate G3）

**变更类型**：组合（前端能力替换 + additive API + 内部运行时安全 + 菜单软删除 migration）
**风险等级**：高（主建模入口、签名游标、dbt profile lease/Docker 清理和菜单可见性同时变化；禁止物理删除旧路由或业务表）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|---|---|---:|---|
| Expand | 新工作台 Shell、关系图 additive `cursor/nextCursor`、canonical owner 嵌入适配 | 是 | 回切 platform/webapp 旧镜像 |
| Migrate | 旧深链继续保留；5 个旧菜单行按固定 marker 软删除 | 是 | changeSet rollback 只恢复同 marker 行 |
| Contract | 8 条 compatibility route、客户旧 API/表删除 | **否** | 两版本零访问与审批前禁止执行 |

本次不新增或删除业务表。关系图 API 只增加可选请求参数和可选响应字段；dts-admin Liquibase 只更新 `portal_menu.deleted`，保留 menu id、角色和 visibility binding。F4 内部 profile lease 契约保持相同 endpoint，只强化续租、释放和清理失败语义。

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|---|---|---:|---|
| `/modeling/workbench` 用户 | `ModelingWorkbenchPage.source-contract.test.ts` | 是 | URL 保留 `planId`，新增可选 `module/workspaceView` |
| 建设规划深链 | `WarehousePlanDetailPage.source-contract.test.ts` | 否 | 原 wildcard 路由与编辑/CAS 逻辑保留 |
| 数据元深链 | `ElementsPage.source-contract.test.ts` | 否 | 默认页面布局保留，仅增加可选 `embedded` |
| 模型、维度、指标、关系图 | `ModelingWorkspacePanels.source-contract.test.ts` | 否 | 工作台懒加载既有 owner，不复制 API |
| 旧建模兼容路由 | `modelingRouteConvergence.source-contract.test.ts` | 否 | 继续进入统一 compatibility page |
| 关系图 API 消费方 | `warehousePlanApi.ts`、`RelationshipGraphPanel.tsx` | 是 | `cursor/nextCursor` 均可选；旧客户端仍可读取首批 |
| Airflow dbt task factory | `dbt_task_factory.py` | 是 | 只消费 `profileLeaseId`；清理未确认时 fail-closed，不释放租约 |
| dts-admin 菜单/角色 | `20260730-01_sprint79_modeling_workspace_menu_convergence.xml` | 是 | 只软删除 5 个旧菜单名，保留 id/binding；rollback 带 marker |

## 3. 回填策略

- 不适用：本次无数据结构和存量数据变更。
- 工作台 URL 新字段均为可选；未知值降级到模块默认视图。
- 菜单 migration 不回填、不删行；只更新当前 `deleted=FALSE` 且名称在 allowlist 中的行。

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

### F3～F5 主线发布回滚基线（2026-07-31）

- 产品变更提交：`51daf1225`、`82d6e8eec`、`56afd9858`、`c87cbf8b8`、`8742e2bfe`、`286ffc68a`。
- 构建树基线：`239fba2c7`。
- E2E 验收测试基线：`32cdc41d4`、`e1658f9d7`、`3e370f46e`、`9953198de`。
- 部署前镜像：
  - `dts-platform`：`sha256:46b0435fc37290e71fe0c995d31b7c724e0cef84c85a12422b549eb4e11d2902`
  - `dts-platform-webapp`：`sha256:6ad5170b1c5ca5665061f00121881f908a58379d52b2db51942c4a8969854b19`
  - `dts-admin`：`sha256:941e5e461e25c8b6f400b750f0ccf04a1b12611f42142d51508626da7835f522`
  - Airflow：镜像不变，`services/dts-airflow/extra` 为只读 bind mount；回滚源版本为 `56afd9858^`。
- 最终运行镜像：
  - `dts-platform`：`sha256:70dbc04c8f8468b084ded7721aed9db59e836967ab53333e0990c2eadf65b188`
  - `dts-platform-webapp`：`sha256:d067edfaac774c9936e694e27a2876a7f5c40089d5ed89faded7e1e4b86004f2`
  - `dts-admin`：`sha256:524a6e79d0e725888ab1fc772a617c93334c6c08c88793565b94a8a636183263`
  - Airflow scheduler/webserver：`sha256:0fb925319a952be2b75595327cc386efec9c16637bb893f72b4f28edacb2d176`
- 回滚标签在部署前创建：
  - `dts-platform:rollback-sprint79-f3f5`
  - `dts-platform-webapp:rollback-sprint79-f3f5`
  - `dts-admin:rollback-sprint79-f3f5`
- 回滚顺序：
  1. 先回切 webapp，再回切 platform；
  2. 对 `20260730-01-sprint79-modeling-workspace-menu-convergence` 执行定向 rollback，或执行 changeSet 内带 `last_modified_by='sprint79-menu-convergence'` 条件的恢复 SQL；
  3. 回切 dts-admin；
  4. 将 Airflow bind-mounted factory 恢复到 `56afd9858^` 对应文件并重启 scheduler/webserver；
  5. 核对 HTTPS、健康状态、菜单和临时身份清理。
- 实际演练：2026-07-31 02:46～02:49 CST，webapp、platform、admin 依次回切到上述部署前镜像，三项镜像 ID 精确匹配，platform/admin health 为 `UP`，HTTPS 为 200；菜单定向 rollback 为 `UPDATE 5`，5 个 menu id 与 5 个 visibility binding 均保留。随后恢复三个候选镜像并重新应用同一 5 行软删除，最终 health/HTTPS/菜单断言再次通过，**PASS_WITH_GAPS**。
- Airflow：重启前运行中 DAG/Task 均为 0；恢复候选后 API 显示 scheduler/triggerer healthy，宿主与容器内 `dbt_task_factory.py` SHA-256 一致。为避免改写共享工作树，本轮未实际回切 bind-mounted Python 源码，Airflow 源码 rollback 仍是 G3 缺口。
- 不可逆部分：无物理删除；新 cursor 在密钥轮换或回切后失效，客户端必须从首批刷新，属于安全预期。

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
- 该次 F2/T04 演练结束时运行态不是候选镜像；候选随后已并入 F3～F5 主线发布门禁。

## 5. 部署顺序与影响面

1. 保存 platform/webapp/admin 当前运行镜像的回滚标签，并记录 Airflow 回滚 Git 版本。
2. 构建 `dts-platform`、`dts-platform-webapp`、`dts-admin` 候选镜像；Airflow 使用 bind-mounted 已提交源码。
3. 先部署 `dts-platform`，验证 health、Liquibase 和关系图 API 兼容。
4. 再部署 `dts-platform-webapp`，验证工作台、指标、关系图和旧深链。
5. 部署 `dts-admin` 应用菜单软删除 migration；核对只命中 allowlist 且 menu id/visibility binding 未删除。
6. 重启 Airflow scheduler/webserver，使 task factory 代码确定重新加载；不重启数据库。
7. 执行一次旧镜像/菜单回切演练，再恢复候选版本并完成统一认证 E2E。

影响面：`dts-platform`、`dts-platform-webapp`、`dts-admin`、Airflow scheduler/webserver 和 `portal_menu` 的 5 个软删除标记。PostgreSQL 业务表、客户 ODS、dbt 工程和 compatibility route 不删除。

## 6. 运行态观测

- 2026-07-30 20:12 CST 验收期间，前端容器曾被并行外部操作置为 `Created`，登录路由短暂返回 404。
- 本轮通过 `docker compose ... up -d --no-deps dts-platform-webapp` 恢复同一最终镜像后，登录路由返回业务 400、认证测试恢复正常。
- 该漂移不来自验收脚本；最终状态再次核对为 `sha256:7b939...`、`running`。
- F2/T01 恢复后最终状态再次核对为 `sha256:b773c...`、`running`；后端、数据库、dbt 和 Airflow 未重启。
- 2026-07-31 首次部署新 admin 时，Spring 因 `application.yml` 中重复 `dts.admin` 键失败启动；已通过 `8742e2bfe` 合并映射，5/5 聚焦测试、生产包、实际容器启动和独立审查通过。
- 随后发现 Compose 未向 admin 注入平台方向令牌；`286ffc68a` 为 app/dev/legacy 三种模式补齐 `DTS_INBOUND_FROM_PLATFORM`。运行态验证：缺失/错误令牌均为 403，正确方向令牌进入业务处理并对不存在用户返回 404。
- 最终统一认证 E2E：`sprint79-modeling-workspace.spec.ts` 1/1 通过，七模块、刷新保持、指标 owner、工具导入和建设计划关系图非零节点/关系均通过；page error、request failure、API 4xx/5xx 为 0；临时身份清理为 `0/0/absent`。
