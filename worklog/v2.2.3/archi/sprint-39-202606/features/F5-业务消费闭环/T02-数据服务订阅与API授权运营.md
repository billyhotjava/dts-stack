# T02: 数据服务订阅与 API 授权运营

**优先级**: P1
**状态**: DONE
**依赖**: F3-T04

## 目标

让数据 API 和数据产品从目录登记升级为可订阅、可授权、可统计的运营闭环。

## 技术设计

- 数据 API 发布绑定治理资产、版本、权限策略和调用限制。
- 数据产品记录 schema、消费方式、SLA、刷新频率、授权状态。
- token、订阅审批、调用统计纳入同一服务视图。
- 新增 `GoldenChainDataServiceSubscriptionService`，统一输出订阅状态、服务视图、secretRef token、调用统计和 `CONSUMABLE` 阶段快照。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/consumption/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/consumption/`

## 验证

- [x] 未授权 token 无法调用受控 API。
- [x] API 服务能展示调用统计和依赖资产。
- [x] focused test: `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainDataServiceSubscriptionServiceTest test`

## 完成标准

- [x] 数据服务中心成为服务运营面，而不只是资产目录。
