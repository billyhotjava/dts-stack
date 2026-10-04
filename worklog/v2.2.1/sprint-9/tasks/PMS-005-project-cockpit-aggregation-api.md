# PMS-005

## 标题

新增项目看板专题聚合 API，为统一入口壳页提供稳定、低耦合的数据契约。

## 范围

- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/`

## 目标

- 避免前端直接拼接多个底层表或杂糅接口
- 为五个主题视图提供清晰聚合结果

## 交付

- `/analytics/api/project-cockpit/summary`
- `/analytics/api/project-cockpit/trends`
- `/analytics/api/project-cockpit/execution`
- `/analytics/api/project-cockpit/risk-attribution`
- `/analytics/api/project-cockpit/major-project-tree`
- `/analytics/api/project-cockpit/data-support`
- `ProjectCockpitResourceIT`

## 验收

- 新接口返回 `200`
- 支持全局筛选参数
- 返回结构稳定，不暴露原始 Excel 形状字段

## 当前进度

- 状态：DONE

## 风险

- 若接口边界定义不清，前端会再次回退到页面内拼装逻辑
