# SD-001

## 标题

补齐 `marketplace` 后端最小闭环，消除 `/analytics/api/marketplace/*` 断层。

## 范围

- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/MarketplaceResource.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/MarketplaceService.java`
- 必要的 asset storage / repository / dto
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/MarketplaceResourceIT.java`

## 目标

- 让 `ScreenMarketplacePage` 的组件/模板列表与安装动作有真实后端承接
- 第一阶段只做实例内资产仓，不引入远端社区同步

## 交付

- `GET /api/marketplace/components`
- `GET /api/marketplace/templates`
- `POST /api/marketplace/components/{id}/install`
- `POST /api/marketplace/templates/{id}/install`
- 最小安装状态与错误回显模型

## 验收

- `curl` 访问 `/analytics/api/marketplace/components` 与 `/analytics/api/marketplace/templates` 返回 `200`
- 安装接口不再返回 `404`
- `MarketplaceResourceIT` 覆盖列表与安装关键路径

## 当前进度

- 状态：TODO
- 备注：建议优先复用现有模板资产与插件 manifest 能力，不另起第四套资产模型

## 风险

- 若一步到位做远端社区源，会显著扩大本 Sprint 范围
