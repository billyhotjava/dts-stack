# 发布安全计划（Gate G3）

**变更类型**：API 增量 + 数据建模前端能力替换  
**风险等级**：中（涉及认证、权限和严格审计；不含数据库 schema 变更或数据回填）  
**状态**：IN_PROGRESS（当前原型符合性重构已构建并部署，待授权真实 E2E 与完整回滚演练）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|---|---|---|---|
| Expand | 新增原子维度创建/恢复 API；既有 ModelSpec、标准、指标和关系 API 保持兼容 | 是 | 回退两个应用镜像 |
| Migrate | 无数据回填；新 API 复用既有维度定义与 ModelSpec 存储 | 否 | - |
| Contract | 不删除数据库结构和既有公共 API | 否 | - |

## 2. 兼容性

| 消费方 | 影响 | 处置 |
|---|---|---|
| `dts-platform-webapp` | 使用新增原子维度 API，并继续消费既有规划、标准、指标、关系和审计 API | 后端先部署并健康，再部署前端 |
| 既有 ModelSpec/DimensionDefinition API 消费者 | 契约未删除；仅保留 `dm:v2:` 为内部幂等命名空间 | 既有普通幂等键保持可用，聚焦回归 57/57 通过 |
| 平台审计消费者 | 新增 `MODELING_DIMENSION_MODEL_CREATE` 动作 | canonical 目录与 fallback 目录同步；成功/失败均严格审计 |
| `dts-admin`、`dts-ingestion`、数据质量和数据资产 | 无本 Sprint 部署修改 | 不重建、不重启；用户工作树改动保持隔离 |

## 3. 数据与不可逆性

- 无 schema migration、无批量回填、无存量数据删除。
- E2E 若需创建验证对象，只允许使用可识别的测试前缀并在验收后按 canonical owner 规则清理；不得写入客户业务元数据。
- 原子创建成功后会生成维度定义和 ModelSpec 版本，属于可审计业务写入；浏览器验收前须确认测试租户和清理边界。

## 4. 回滚

部署前回滚锚点：

- `dts-platform:rollback-s84-predeploy-20260802` → `sha256:5bfc2f9b53e4b7bd1b893db5be7882dc5ddee78f57138982d3fe8f5f9326be51`
- `dts-platform-webapp:rollback-s84-predeploy-20260802` → `sha256:c1ce23b9c4a8f2b32646409443231313c923b4d8ec844d3fb5044f9086e06fd1`

回滚步骤：

1. 将上述两个 rollback tag 重新标记为 compose 使用的 `1.0.0` tag。
2. 执行 `docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform dts-platform-webapp`。
3. 等待 `dts-platform` healthy，检查前端入口、API 和审计日志；失败则停止进一步操作。

当前演练结果：rollback tag 已解析并核对到原镜像 digest；新镜像已按顺序部署并恢复健康，但实际容器回滚/前滚尚未演练，因此 Gate G3 暂不标记 PASS。

## 5. 部署顺序与影响面

1. 只构建 `dts-platform` 与 `dts-platform-webapp`，不构建全仓镜像。
2. 先替换 `dts-platform`，等待健康并确认新增 API/审计目录加载正常。
3. 再替换 `dts-platform-webapp`，检查入口资源可访问。
4. 运行一次 Sprint-83/84 联合真实认证 E2E；通过后补齐回滚演练和 Gate G3 证据。

影响面：数据建模页面、原子维度创建/恢复、ModelSpec 内部幂等边界、建模审计。未触碰数据库 schema、数据集成容器、数据质量和数据资产后端。

## 6. 当前制品与部署边界

- 2026-08-03 当前制品：Biome 通过；最终集中回归 Vitest `13 files / 73 tests`、Node 契约 `8/8`；TypeScript 无错，Chrome 95 target 生产制品与镜像构建成功。
- `dts-platform:1.0.0` 保持既有 healthy，本轮未重建、未重启。
- `dts-platform-webapp:1.0.0`：`sha256:2bf603f5ce642319182ae4a89cc26e32063bc0ce910d9a7e6783f5b9f7bdc767`，容器 running；容器内首页与 `https://bi.yuzhicloud.com/data-modeling` 均为 HTTP 200。
- 部署前前端回滚锚点：`dts-platform-webapp:rollback-s83-s84-pre-e2e-20260803` → `sha256:bbcf508abdca82339e3c98d0dfab275f642d094164e9be77963be34c4664172b`。
- 启动日志仍有既存 `DBT_MODEL_SYNC` fallback 告警；它不属于 Sprint-84，未顺带扩修。
