# F7 首次建模修复与验证（2026-09-10）

## 源码与单次聚焦 review

- 核心提交 `f3dd879e5b11d142063626443e7839e16245529f`；认证状态补严 `13261975afe1889a170c2e7ebea79051c5a44a1b`；完整首次保存回归 `26cf6c08804b92cb0818efcb5c3a75e719c6dc27`。测试夹具/编译清单最终提交 `30538c203386d8bb5123e6781af77909a075141e`。均由开发目录 commit/push，部署目录 clean `git pull --ff-only` 同步。
- GitNexus 上游分析：ModelSpecPlanWriteAccessAdapter.canMaintain 为 HIGH（22 直接调用），WarehousePlanAuthorizationGuard.requirePlanMaintenance 为 CRITICAL（7 直接调用）；改前已告知。初次提交 detect_changes 为 HIGH，覆盖预期草稿保存/替换与权限流程；未改全局角色常量、来源有效性、模型 CAS、dbt 编译/物化或数据库迁移。
- 一次聚焦源码 review 覆盖三条创建入口、事务传播、缺省/显式上下文、重放、认证/租户、前端失败恢复。发现并修正 SecurityUtils.isAuthenticated 本身未检查 Authentication.isAuthenticated：本次三个访问点补严校验；后台 owner 回退仅允许完全没有 Authentication 的任务。
- 重用 WarehousePlanApplicationService 创建 plan、policy、幂等快照；协调服务不另建台账。新默认上下文与 callback 中模型保存共用事务，冷启动使用事务锁，热路径只读查找。

## 实测结果

| 阶段 | 结果 | 证据 |
|---|---|---|
| 静态检查 | PASS | git diff --check；GitNexus impact/detect_changes |
| 前端定向回归 | PASS，81/81 | `/tmp/f7-frontend-tests-final.log`；4 个文件，覆盖定义/完整保存、重试和现有工作台兼容路径 |
| 前端正式源码构建 | PASS | `/tmp/f7-frontend-build.log`；pnpm build，TypeScript + LEGACY_BROWSER_BUILD=1，3m49s；后续变更仅 Java/测试，前端产品源码与构建提交一致 |
| 后端专项 | PASS，55/55，无跳过 | `/tmp/f7-backend-tests-green.log`；[持久化测试明细](F7-test-results-20260910.json) |
| 正式镜像/离线包 | 未执行 | 本轮尚未产出新交付包，不复用旧包冒充当前修复 |
| 容器部署 | 未执行 | 当前运行容器尚未应用本轮源码 |
| 真实页面/Chrome95 | GAP | CUA 当前没有可用浏览器；未以真实空库页面成功替代或推定验收 |

后端首次宿主 Maven 遇到旧 root 构建产物权限，已改用仓库正式 Maven 容器。首轮测试编译发现新用例 AssertJ 泛型推断歧义，已在源码提交修正；不是运行业务库故障。第二轮 49 项发现旧 JWT 夹具没有认证标记、跨租户 Mockito 缺少返回值、维度接口仍断言旧角色常量；已修正测试并将原先不在编译清单中的 WarehousePlanAuthorizationGuardTest 加入。最终 55 项全部通过，未通过放宽生产校验消除测试失败。

## 可复现专项入口

部署目录 `/opt/prod/s10/deploy`：Maven 使用仓库构建镜像 `maven:3.9.9-eclipse-temurin-21`、现有 Maven 缓存及 Docker socket（仅隔离 Testcontainers），执行 `mvn -B -f source/pom.xml -pl dts-platform -am -Dtest=ModelingContextInitializationPostgresTest,ModelSpecPlanWriteAccessAdapterTest,ModelSpecDomainWriteAccessAdapterTest,WarehousePlanAuthorizationGuardTest,ModelDraftOperationResourceTest,DimensionModelResourceTest,ModelSpecResourceTest,ModelSpecReservedIdempotencyBoundaryTest,ModelDraftSaveApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。

前端目录执行 `vitest run src/api/modelSpecApi.initialization.test.ts src/api/services/modelingImportContextService.creation.test.ts src/pages/data-modeling/prototype/services/modelDefinitionCreation.test.ts src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts` 与 `pnpm build`。

PG 专项使用 postgres:17.6 隔离实例和最小必要 schema，运行真实上下文协调服务、WarehousePlanApplicationService、Spring 事务代理；模型写入 callback 用隔离测试表验证事务行为。它证明初始化/并发/回滚，不能替代整库 Liquibase 或真实页面端到端验收。现有模型应用服务及三条 REST 边界另有专项覆盖。

## IT-44–47 剩余验收与恢复

1. 从最终提交构建 platform/webapp 正式镜像和交付包；记录 SHA、镜像 ID、包校验和，按原 Compose 定向发布。
2. 使用全新隔离业务库完成正常迁移和目录基础数据；必须确认建模上下文为零。普通菜单用户打开工作台，分别新建 DWD 和 DIM，保存后进入实现配置；无管理员初始化操作。
3. 页面覆盖首次空态、保存 loading、失败保留输入/操作 ID、成功返回真实模型 ID/planId，刷新和再次保存继续编辑；匿名/跨租户/无效来源仍拒绝。Chrome95 和离线安装分项留证。
4. 本轮无 schema 迁移，已有显式 planId 与初始化产生的记录均是原表结构；需要退回应用时使用先前正式镜像，不删除上下文或模型数据，不手改数据库。

源码/专项通过不代表 S10DC-80 现场闭环，F7-T01–T04 和 F7 保持 IN_PROGRESS。
