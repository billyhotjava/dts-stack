# Metrics Frontend Routing Evidence

## 目标

验证指标与语义中心前端已从 `dts-platform-webapp` 拆到 `dts-metrics` 服务，`platform-webapp` 只保留菜单入口和旧入口整页跳转，不再承载旧指标/语义页面。

## 覆盖变更

- `dts-metrics` 提供 `/metrics/**` 静态前端入口。
- `docker-compose-app.yml` 将 `/metrics` 路由到 `dts-metrics`，并从 `dts-platform-webapp` 路由中排除。
- `dts-platform-webapp` 移除 `/metrics/**`、`/modeling/semantic-center/**`、`/bi/semantic-modeling` 的旧页面注册，改为整页跳转。
- `dts-platform-webapp` 动态页面 glob 排除 `pages/metrics/**` 和旧 `pages/modeling/Semantic*.tsx`。

## 验证命令

```bash
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics test
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics -DskipTests package
```

```bash
pnpm build
```

运行目录：

```text
source/dts-platform-webapp
```

```bash
docker compose -f docker-compose-app.yml config | rg -n 'dts-metrics-(api|ui)|dts-platform-ui.rule|PathPrefix\(`/metrics'
```

```bash
java -jar dts-metrics/target/dts-metrics-2.2.3-SNAPSHOT.jar --server.port=18084
curl -fsS http://127.0.0.1:18084/metrics/center
curl -fsS -I http://127.0.0.1:18084/metrics/semantic/metrics
curl -fsS http://127.0.0.1:18084/api/metrics/health
curl -fsS -X POST -H 'Content-Type: text/yaml' --data-binary 'pack_id: demo
pack_name: Demo
version: 0.1.0
industry: project
edition_required: professional
files:
  domains: domains.yml
  business_objects: business_objects.yml
  dimensions: dimensions.yml
  metrics: metrics.yml
  models: models.yml
  datasets: datasets.yml
dependencies: {}
' http://127.0.0.1:18084/api/metrics/packs/validate
```

## 验证结果

- `dts-metrics` 单元测试通过。
- `dts-metrics` package 通过；本机历史 `target` 目录存在 root 只读 jar，已修正构建产物权限后重跑通过。
- `platform-webapp` 生产构建通过。
- `source/dts-platform-webapp/dist/assets` 中未再生成旧指标/语义中心页面 chunk：
  - `MetricCenterPage-*`
  - `MetricDictionaryPage-*`
  - `MetricOperationsPage-*`
  - `SemanticMetricDesignerPage-*`
  - `SemanticDatasetsPage-*`
  - `SemanticSubjectsPage-*`
  - `SemanticObjectsPage-*`
  - `SemanticPublishPage-*`
  - `SemanticRunsPage-*`
  - `SemanticOverviewPage-*`
- `dts-metrics` jar 中包含：
  - `/metrics/index.html`
  - `/metrics/assets/metrics-app.css`
  - `/metrics/assets/metrics-app.js`
- `/metrics/center` 返回 HTML。
- `/metrics/semantic/metrics` 返回 `200 OK`，内容类型为 `text/html`。
- `/api/metrics/health` 返回 `status=UP`。
- 合法 `metric-pack v0.1` manifest 校验返回 `valid=true`。

## 剩余范围

- capability 驱动的菜单显隐仍需在后续任务统一处理。
- `/api/semantic/**` 兼容代理仍需单独实现和记录弃用日志。
- 完整指标语义 CRUD、DWS/ADS 生成、dbt 发布网关仍属于 F3/F4/F6 后续范围。
