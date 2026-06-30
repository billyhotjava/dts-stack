# T02: 角色默认项与 locale 对齐

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

把默认角色可见项和国际化 key 从旧 `metrics*` 入口迁移到新的 `studioMetric*` / `studioSemantic*` 入口。

## 技术设计

- 更新 `role-menu-defaults.json`：
  - 移除或替换 `/bi-apps/metrics/*` 默认项。
  - 新增 `/modeling/metric-workbench` 与 `/modeling/semantic/*` 默认项。
- 更新 `zh_CN/sys.json` 与 `en_US/sys.json`：
  - 补齐 `sys.nav.portal.studioMetricModeling` 等 key。
  - 不再依赖旧 `metricsWorkbench` / `metricsSemantic` 默认角色项。
- 保持已有角色绑定不被强删；若旧绑定存在，通过路由兼容迁移。

## 影响范围

- `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`
- `source/dts-platform-webapp/src/locales/lang/zh_CN/sys.json`
- `source/dts-platform-webapp/src/locales/lang/en_US/sys.json`

## 验证

- [x] role defaults 不再指向 `/bi-apps/metrics/*`。
- [x] 新 titleKey 在中英文 locale 中存在。

## 完成标准

- [x] 新装环境默认角色能看到平台指标建模入口。
- [x] 旧角色绑定不会因为 seed 变更直接丢失业务访问。
