# T01: 页面链路 source-contract

**优先级**: P0  
**状态**: READY  
**依赖**: F3/T02

## 目标

固定规划、标准和维度建模页面的路由、上下文参数和用户动作。

## 技术设计

- 新增 `WarehousePlanningLoop.source-contract.test.ts`（journey 目录或 governance 目录，按最终 helper 位置定）：静态断言三页源码含规划参数解析、来源条 testid、跳转 URL 构造。
- 复用 Sprint-62 `JourneyRouteConsistency` 机制：规划链路涉及的 route 全部对 static-routes/dynamic-resolver 双源校验。

## 影响范围

- 新增 1 个 source-contract 测试文件
- 不改产品代码（纯守卫）

## 验证

- [ ] 主题域页进入数据元页。
- [ ] 数据元页生成草稿进入低代码/SQL 维度模式。
- [ ] SQL 页面回到规划时上下文完整。
