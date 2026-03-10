# VIS-001: VisualizationResource 去硬编码，落真实聚合服务

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/VisualizationResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/visualization/`
- 相关 repository / dto / test

## 目标

- 去掉 `/api/vis/*` 中的硬编码样例数据
- 将 dashboards、cockpit、projects、finance、supply、hr 摘要下沉到服务层
- 保留分类过滤与审计能力

## 交付

- `VisualizationSummaryService` 或等价聚合服务
- `VisualizationResource` 缩成薄控制器
- 至少一组真实数据源映射规则
- 覆盖分类过滤与空态的测试

## 验收

- `/api/vis/dashboards` 返回来自真实数据源/链接表的数据，而不是固定 `List.of(...)`
- `/api/vis/*/summary` 在无数据时返回明确空态，而不是 demo 值
- 审计记录不回退

## 当前进度

- 状态：TODO
- 备注：需要先明确 finance/supply/hr 的真实聚合口径
