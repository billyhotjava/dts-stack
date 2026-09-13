# T01: 新契约链路 source-contract

**优先级**: P0
**状态**: DONE
**依赖**: F1-F3

## 目标

静态守卫四类新对象在页面链路中的解析、展示与透传。

## 技术设计

- 新增/扩展 governance 目录契约测试：业务过程区、矩阵面板、grain 表单、注册表消费的 testid 与源码断言。
- 扩展 JourneyRouteConsistency：processId 相关路由与参数收集。

## 影响范围

- 契约测试文件（新增 1-2 个），不改产品代码

## 验证

- [x] 纯 source-contract 断言可在 `node --experimental-strip-types --test` 下运行，Vitest-only 用例由 Vitest 执行。
