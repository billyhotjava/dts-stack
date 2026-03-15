# SD-006

## 标题

为大屏资产链路补齐后端集成测试基线。

## 范围

- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/MarketplaceResourceIT.java`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenTemplateResourceIT.java`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenIndustryPackResourceIT.java`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenPluginResourceIT.java`

## 目标

- 把 marketplace、模板资产、行业包、插件清单最关键的 API 路径纳入后端回归
- 显式防止 404 / 403 / 回归性契约断裂

## 交付

- 4 组最小 IT
- 覆盖正常路径与关键权限边界

## 验收

- `./mvnw test` 中能执行到大屏资产专项 IT
- 关键 API 断层会被测试直接打爆，而不是靠页面发现

## 当前进度

- 状态：DONE
- 备注：已补 marketplace、screen-plugins、screen-templates、screen-packs 四组最小 IT，并实际跑通定向 Maven 测试

## 风险

- 若测试夹具过重，执行成本会快速上升
