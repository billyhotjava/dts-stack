# Sprint-53 集成测试计划

**状态**: DONE  
**目标**: 证明 `dts-metrics` 已从默认产品路径退役，同时旧客户链接可兼容到平台新页面。

## 验证项

| 项目 | 命令/方式 | 预期结果 |
|------|-----------|----------|
| 菜单/角色 source-contract | `node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/analytics/pages/Sprint45Consumption.source-contract.test.ts` | 新指标菜单、role defaults、locale、旧路由映射均通过 |
| Metric workbench contract | `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts` | Sprint-52 指标页面仍绿 |
| Semantic object mapping helper | `pnpm exec vitest run src/pages/modeling/semanticObjectMappings.helpers.test.ts` | join 图按主表角色选择源节点 |
| Semantic modeling backend regression | `./mvnw -q -pl dts-platform -Dtest=SemanticModelingServiceTest test` | executable SQL 与 dbt 共用主表角色语义 |
| Workbench golden route contract | `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts` | `MODEL_READY` 不再指向旧 semantic-center |
| Runtime retirement contract | `node --test src/routes/sections/dashboard/metricsRuntimeRetirement.source-contract.test.ts` | 默认 compose/init/build/platform trust 均不再默认带 `dts-metrics`；legacy 回滚面保留 |
| TypeScript | `pnpm exec tsc --noEmit` | 0 errors |
| Chrome 95 扫描 | `grep -rn "oklch\\|:has(\\|@container" src/pages/modeling src/routes/sections/dashboard` | 0 matches |
| Compose 默认渲染 | `docker compose -f docker-compose-app.yml config --services` + targeted `rg` | 默认无 `dts-metrics` 服务、`/api/metrics` router、`/metrics` router |
| Legacy 回滚渲染 | `docker compose -f docker-compose.legacy.yml config --services` | legacy 路径仍可显式保留 `dts-metrics` |
| init/env 检查 | `metricsRuntimeRetirement.source-contract.test.ts` + `bash -n init.sh` | 默认 `.env` 不再生成 metrics triplet；legacy flag 可显式输出 |
| 构建脚本检查 | `metricsRuntimeRetirement.source-contract.test.ts` + `bash -n builds/dts-build.sh` | default all 不再构建 metrics image；显式 `--image dts-metrics --legacy` 保留 |

## 证据记录

实施时在本文件追加：

```text
执行时间:
工作目录:
Git HEAD:

[1] source-contract
[2] tsc
[3] compose config
[4] build/init grep
[5] manual smoke / screenshot
```

```text
执行时间: 2026-06-30 14:32 Asia/Shanghai
工作目录: /opt/prod/s10/v2.2.3
Git HEAD: 22230f03c

[1] review 修复后的旧 metrics 路由兼容
工作目录: /opt/prod/s10/v2.2.3/source/dts-platform-webapp
node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts
结果: 3/3 pass；`/metrics/operations`、`/bi-apps/metrics/f5-security-it`、`/metrics/runs` 均映射到 `/modeling/semantic/runs`

[2] 业务对象表映射 join graph 行为
pnpm exec vitest run src/pages/modeling/semanticObjectMappings.helpers.test.ts
结果: 1 file / 2 tests pass；主表不在第一行时仍从主表节点出边

[3] 平台语义建模后端回归
工作目录: /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=SemanticModelingServiceTest test
结果: 通过；仅有既有 SLF4J 多 provider 提示

[4] 页面契约与类型
工作目录: /opt/prod/s10/v2.2.3/source/dts-platform-webapp
node --test src/pages/modeling/metricWorkbench.source-contract.test.ts
结果: 7/7 pass

pnpm exec tsc --noEmit
结果: 0 errors

[5] diff
工作目录: /opt/prod/s10/v2.2.3
git diff --check
结果: 0 issues
```

```text
执行时间: 2026-06-30 13:01 Asia/Shanghai
工作目录: /opt/prod/s10/v2.2.3/source/dts-platform-webapp
Git HEAD: 22230f03c

[1] source-contract
node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/analytics/pages/Sprint45Consumption.source-contract.test.ts
结果: 14/14 pass

node --test src/pages/modeling/metricWorkbench.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts
结果: 14/14 pass

[2] workbench model contract
pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts
结果: 1 file / 6 tests pass

[3] TypeScript
pnpm exec tsc --noEmit
结果: 0 errors

[4] JSON / diff / GitNexus
node -e "JSON.parse(...)"
结果: role-menu-defaults、zh_CN/sys、en_US/sys parse ok

git diff --check
结果: 0 issues

GitNexus detect_changes(scope=all)
结果: risk_level=low, affected_processes=[]

[5] 后续补证
compose config、init/env、build 脚本退役已在 2026-06-30 13:44 证据块补齐。
```

```text
执行时间: 2026-06-30 13:44 Asia/Shanghai
工作目录: /opt/prod/s10/v2.2.3
Git HEAD: 22230f03c

[1] runtime retirement source-contract
工作目录: /opt/prod/s10/v2.2.3/source/dts-platform-webapp
node --test src/routes/sections/dashboard/metricsRuntimeRetirement.source-contract.test.ts src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts
结果: 12/12 pass

[2] broader platform-webapp source-contract + TypeScript
node --test src/routes/sections/dashboard/metricsRuntimeRetirement.source-contract.test.ts src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/analytics/pages/Sprint45Consumption.source-contract.test.ts src/pages/modeling/metricWorkbench.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts
结果: 34/34 pass

pnpm exec tsc --noEmit
结果: 0 errors

[3] compose 默认退役
工作目录: /opt/prod/s10/v2.2.3
docker compose -f docker-compose-app.yml config --services
结果: 输出服务列表不包含 dts-metrics

docker compose -f docker-compose-app.yml config | rg 'dts-metrics|/api/metrics|PathPrefix\(`/metrics`\)'
结果: 0 matches

[4] legacy 回滚面
docker compose -f docker-compose.legacy.yml config --services | rg '^dts-metrics$|^dts-platform$'
结果: dts-platform、dts-metrics 均存在；仅有 compose version obsolete 既有警告

[5] init/build 语法与平台后端回归
bash -n init.sh && bash -n builds/dts-build.sh
结果: 通过

工作目录: /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test
结果: 通过；测试输出包含既有 SLF4J 多 provider 提示和预期 service_auth_denied 警告
```

```text
执行时间: 2026-06-30 13:58 Asia/Shanghai
工作目录: /opt/prod/s10/v2.2.3
Git HEAD: 22230f03c

[1] Sprint-53 source-contract
工作目录: /opt/prod/s10/v2.2.3/source/dts-platform-webapp
node --test src/routes/sections/dashboard/metricsRuntimeRetirement.source-contract.test.ts src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/analytics/pages/Sprint45Consumption.source-contract.test.ts src/pages/modeling/metricWorkbench.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts
结果: 36/36 pass

[2] TypeScript / Chrome 95
pnpm exec tsc --noEmit
结果: 0 errors

rg -n "oklch|:has\\(|@container" src/pages/modeling src/routes/sections/dashboard -g '!*.test.ts' -g '!*.source-contract.test.ts'
结果: 0 matches

[3] default/legacy compose
docker compose -f docker-compose-app.yml config --services | rg '^dts-metrics$'
结果: 0 matches

docker compose -f docker-compose-app.yml config | rg 'dts-metrics|/api/metrics|PathPrefix\(`/metrics`\)'
结果: 0 matches

docker compose -f docker-compose.legacy.yml config --services | rg '^dts-metrics$|^dts-platform$'
结果: dts-platform、dts-metrics 均存在；仅有 compose version obsolete 既有警告

[4] init/build/backend
bash -n init.sh && bash -n builds/dts-build.sh
结果: 通过

工作目录: /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test
结果: 通过；仅有既有 SLF4J 多 provider 提示和预期 service_auth_denied 警告

[5] diff / GitNexus
git diff --check
结果: 0 issues

GitNexus detect_changes(scope=all)
结果: risk_level=low, affected_processes=[]
```

## 遗留项模板

- 需要保留的旧链接：
- 需要 Sprint-54 接住的黄金线能力：
- 需要 Sprint-55 再评估删除的 dts-metrics 源码/API：
