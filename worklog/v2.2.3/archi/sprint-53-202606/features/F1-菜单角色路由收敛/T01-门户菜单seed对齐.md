# T01: 门户菜单 seed 对齐

**优先级**: P0  
**状态**: DONE  
**依赖**: F0/T01

## 目标

确认并完善用户当前对 `portal-menu-seed.json` 的菜单迁移：旧 BI 下“指标与语义”退役，新“指标建模”落在数据开发/建模承载区。

## 技术设计

- 保留当前本地修改方向：删除 BI 应用下 `metrics` 分组。
- 使用数据开发下 `metric-modeling` 分组承载：
  - `/modeling/metric-workbench`
  - `/modeling/semantic/subjects`
  - `/modeling/semantic/objects`
  - `/modeling/semantic/metrics`
  - `/modeling/semantic/models`
  - `/modeling/semantic/publish`
  - `/modeling/semantic/runs`
- 检查菜单 title、titleKey、path 和 externalLink 是否与路由一致。

## 影响范围

- `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`

## 验证

- [x] `rg "bi-apps/metrics" portal-menu-seed.json` 无匹配。
- [x] `rg "modeling/semantic|modeling/metric-workbench" portal-menu-seed.json` 匹配 7 个平台入口。

## 完成标准

- [x] 门户默认菜单不再出现旧 dts-metrics 服务入口。
