# Sprint-54 集成测试计划

**状态**: DONE  
**目标**: 证明指标建模 UI 已形成统一工作区，运行监控已收敛到任务运维中心，主题域只引用数据治理中心主题域。

## 验证项

| 项目 | 命令/方式 | 预期结果 |
|------|-----------|----------|
| 指标工作台 source-contract | `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts` | 工作台、语义页、运维入口契约通过 |
| 菜单/路由 source-contract | `node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts` | 运行监控菜单迁移和旧链接兼容通过 |
| TypeScript | `pnpm exec tsc --noEmit` | 0 errors |
| Chrome 95 扫描 | `rg -n "oklch|:has\\(|@container" src/pages/modeling src/routes/sections/dashboard -g '!*.test.ts' -g '!*.source-contract.test.ts'` | 0 matches |
| GitNexus | `detect_changes(scope=all)` | risk_level 非 HIGH/CRITICAL |

## 证据记录

### 2026-06-30 静态验证

- `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts`
  - 结果: PASS, 7/7
  - 覆盖: 指标工作台统一工作区、语义页面真实实现、发布链路、运行监控退役兼容入口。
- `node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts`
  - 结果: PASS, 6/6
  - 覆盖: 旧 metrics 路由映射、菜单/角色默认项、运行监控迁移到任务运维中心。
- `pnpm exec tsc --noEmit`
  - 结果: PASS, 0 errors
- `pnpm build`
  - 结果: PASS
  - 备注: 仅出现既有 Vite chunk size warning 和 Browserslist 数据过期提示。
- `rg -n "oklch|:has\\(|@container" src/pages/modeling src/pages/ops src/routes/sections/dashboard -g '!*.test.ts' -g '!*.source-contract.test.ts'`
  - 结果: PASS, 0 matches
- `git diff --check`
  - 结果: PASS
- `gitnexus detect_changes(scope=all)`
  - 结果: PASS, risk_level=low, affected_processes=0

### 2026-06-30 治理主题域收敛与运行态菜单清理

- `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts`
  - 结果: PASS, 16/16
  - 覆盖: 指标建模主题域入口跳转到 `/governance/subjects`，业务对象引用治理主题域，旧 `/modeling/semantic/subjects` 路由兼容但不再作为独立模块出现。
- `./mvnw -ntp -Dtest=SemanticModelingServiceTest test`
  - 结果: PASS, 15/15
  - 覆盖: `/api/semantic/subject-domains` 从 `catalog_domain` 读取，业务对象创建/更新补兼容投影，诊断菜单指向治理中心主题域。
- `pnpm exec tsc --noEmit`
  - 结果: PASS, 0 errors
- `pnpm build`
  - 结果: PASS
  - 备注: 仅出现既有 Vite chunk size warning 和 Browserslist 数据过期提示。
- `xmllint --noout source/dts-admin/src/main/resources/config/liquibase/changelog/20260630-01_remove_metric_modeling_subjects_menu.xml source/dts-admin/src/main/resources/config/liquibase/master.xml`
  - 结果: PASS
- `MAVEN_UNRESTRICTED=1 ./builds/dts-build.sh --image dts-admin dts-platform dts-platform-webapp --no-save`
  - 结果: PASS
  - 产物: `dts-admin:1.0.0`, `dts-platform:1.0.0`, `dts-platform-webapp:1.0.0`
- `MAVEN_UNRESTRICTED=1 ./builds/dts-build.sh --image dts-admin --no-save`
  - 结果: PASS
  - 产物: `dts-admin:1.0.0` = `sha256:2a4885001f60d6fb4a145bfc660e0ba88c2663f515eb51717baf487c6e418d02`
- `docker compose -f docker-compose-app.yml up -d --force-recreate --no-deps dts-admin dts-platform dts-platform-webapp`
  - 结果: PASS
  - 状态: `dts-admin` healthy, `dts-platform` healthy, `dts-platform-webapp` up
  - 备注: compose 仍提示 orphan `v223-dts-metrics-1`，本次未使用 `--remove-orphans` 删除。
- 运行态菜单 SQL smoke
  - `databasechangelog` 已记录 `20260630-01-remove-metric-modeling-subjects-menu`。
  - `portal_menu` 中旧 `/modeling/semantic/subjects` / `studio/metric-modeling/subjects` 不再返回，只保留 `/governance/subjects`。
- `docker compose -f docker-compose-app.yml exec -T dts-admin curl -fsS http://127.0.0.1:8081/management/health`
  - 结果: PASS, `{"status":"UP","groups":["liveness","readiness"]}`

### 2026-06-30 浏览器 smoke

- `pnpm dev --host 0.0.0.0`
  - 结果: PASS
  - 地址: `http://localhost:3001/#/modeling/metric-workbench`
- Playwright DOM 检查
  - 结果: PASS
  - 断言: `semantic-workspace-frame=true`, `metric-workbench-page=true`, 页面包含“指标工作台”“指标关系画布”“任务运维中心”。
  - 备注: 本地 dev token 下 `/api/semantic/*` 代理返回 500，页面按空态渲染；属于后端/会话数据不可用，不是本次 UI 崩溃。

### 2026-06-30 容器重建与流程条增强

- `MAVEN_UNRESTRICTED=1 ./builds/dts-build.sh --image dts-admin dts-platform-webapp --no-save`
  - 结果: PASS
  - 产物: `dts-admin:1.0.0`, `dts-platform-webapp:1.0.0`
- `docker compose -f docker-compose-app.yml up -d --force-recreate --no-deps dts-admin dts-platform-webapp`
  - 结果: PASS
  - 状态: `dts-admin` healthy, `dts-platform-webapp` up
- `docker compose -f docker-compose-app.yml exec -T dts-platform-webapp sh -lc 'wget -qO- http://127.0.0.1/ | head -c 200'`
  - 结果: PASS, 返回平台 HTML
- `curl -k -sS -o /tmp/dts-admin-health.out -w '%{http_code}\n' http://127.0.0.1:38012/management/health`
  - 结果: PASS, `200`, `{"status":"UP","groups":["liveness","readiness"]}`
- 二次页面增强静态验证
  - `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts`: PASS, 8/8
  - `pnpm exec tsc --noEmit`: PASS, 0 errors
  - Chrome 95 扫描: PASS, 0 matches
  - `git diff --check`: PASS
  - 覆盖: 统一工作区新增“建模流程 / 当前步骤 / 下一步”状态条，六个指标建模页面共享。
- `./builds/dts-build.sh --image dts-platform-webapp --no-save`
  - 结果: PASS
  - 最新镜像: `dts-platform-webapp:1.0.0` = `sha256:f08b5fd34f3550bb4c5b3066eae377b4008f9815985d71281f3f34298d34ff53`
- `docker compose -f docker-compose-app.yml up -d --force-recreate --no-deps dts-platform-webapp`
  - 结果: PASS
  - 状态: `dts-platform-webapp` up，容器内 `wget -qO- http://127.0.0.1/` 返回平台 HTML。
- 最终变更检测
  - `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts`: PASS, 14/14
  - `gitnexus detect_changes(scope=all)`: PASS, risk_level=low, affected_processes=0
