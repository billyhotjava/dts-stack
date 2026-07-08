# T01: Source-contract 与菜单契约

**优先级**: P0
**状态**: DONE
**依赖**: F1-T02

## 目标

先写契约测试，固定低代码入口与现有 ELT/指标建模页面的关系，防止后续编码只做孤立页面。

## 技术设计

- 前端 source-contract 覆盖：
  - 数据开发菜单包含低代码入口。
  - SQL/脚本/任务编排高级入口仍存在。
  - 指标建模仍为独立一级分区。
  - 低代码页面包含六步流程。
  - 指标工作台可接收低代码上下文。
  - 主流程文案不出现“写 SQL”作为必需动作。
- 后端菜单 seed 契约覆盖：
  - `portal-menu-seed.json` 的新增菜单。
  - `role-menu-defaults.json` 的默认可见性决策。

## 影响范围

- `source/dts-platform-webapp/src/routes/sections/dashboard/*source-contract.test.ts`
- `source/dts-platform-webapp/src/pages/studio/*source-contract.test.ts`
- `source/dts-platform-webapp/src/pages/modeling/*source-contract.test.ts`
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuSeedDefaultsContractTest.java`

## 验证

- [x] 测试先失败，证明缺口真实存在。
- [x] 实现后测试通过。

## 完成标准

- [x] 契约测试能防止低代码入口和指标/ELT再次割裂。

