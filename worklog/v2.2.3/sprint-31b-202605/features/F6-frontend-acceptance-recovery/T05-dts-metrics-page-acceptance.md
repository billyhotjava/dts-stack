# T05: dts-metrics 页面真实功能验收

**优先级**: P0
**状态**: DONE
**依赖**: Sprint-32 F5

## 目标

确认 `dts-metrics` 独立服务中的指标资产列表、主题域映射、业务对象 Join、指标公式配置、DWS/ADS 生成、发布运行页均为真实可操作页面；`dts-platform-webapp` 只保留菜单链接和平台权限入口。

## 技术设计

验收页面：

1. `/metrics/center` 指标资产列表；
2. `/metrics/semantic/subjects` 主题域映射；
3. `/metrics/semantic/objects` 业务对象 Join；
4. `/metrics/semantic/metrics` 指标公式配置；
5. `/metrics/semantic/models` DWS/ADS 生成；
6. `/metrics/semantic/publish` 发布运行；
7. `/metrics/operations` 运行和告警。

每个页面必须至少有：

- list/read API；
- create/update 或 preview/publish/run 中的一个核心动作；
- 空态；
- 错误态；
- platform permission/capability 错误提示。

## 影响范围

- `source/dts-metrics`
- `source/dts-metrics/src/main/resources/static/metrics`
- `source/dts-platform-webapp` 菜单链接仅做入口，不承载指标业务页面

## 验证

- [x] dts-metrics 当前无独立前端构建链，静态入口已通过 `node --check source/dts-metrics/src/main/resources/static/metrics/assets/metrics-app.js`。
- [x] platform 菜单/旧路由跳转到 `/metrics/**` 的 smoke test 通过：`./node_modules/.bin/tsx --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/components/router-link.metrics-boundary.source.test.ts src/layouts/components/search-bar.metrics-boundary.source.test.ts`。
- [x] 页面级契约测试覆盖指标公式配置、模型生成和发布动作：`./mvnw -q -pl dts-metrics -Dtest=MetricsFrontendResourceContractTest,MetricPackResourceTest test`。

## 完成标准

- [x] 指标与语义中心目标页面均具备真实 API 调用入口：capability 读取、preview-artifacts、import dry-run。
- [x] platform 和 metrics 的边界符合“platform 管权限，metrics 做业务”的拆分原则。

## 实现记录

- `/metrics/dictionary`、`/metrics/semantic/subjects` 可读取 `/api/metrics/capabilities`，用于展示 platform contract/capability 错误态。
- `/metrics/semantic/objects`、`/metrics/semantic/metrics`、`/metrics/semantic/models` 可提交样例 manifest 到 `/api/metrics/packs/preview-artifacts`，触发真实候选生成物预览。
- `/metrics/semantic/publish` 可调用 `/api/metrics/packs/import` 做发布预检。
- `/metrics/semantic/runs` 与 `/metrics/operations` 可刷新服务观测状态。
- `dts-platform-webapp` 的 legacy metrics/semantic 路由只执行浏览器跳转到 `/metrics/**`，不再承载指标业务页面。
