# T04: 跨tab冲突、idle timeout与GPMC drill回归验证

**优先级**: P0  
**状态**: READY  
**依赖**: T01,T02,T03

## 目标

把这次最容易复发的场景固化为回归矩阵，确保 session 统一不是“代码看起来更整洁”，而是实际把 GPMC drill 和多 tab 顶号问题压住。

## 技术设计

- 设计覆盖以下路径的自动/半自动回归：
  - platform drill 页停留超过 refresh 周期
  - 同域多 tab 登录、顶号与登出广播
  - admin 与 platform 同时登录且互不影响
  - idle timeout 后重新登录回原页面
- 优先补单元测试、浏览器级 smoke 和手工验收脚本。

## 影响范围

- `source/dts-platform-webapp/src/**/*.test.*`
- `source/dts-admin-webapp/src/**/*.test.*`
- `tests/web-e2e/**`
- `worklog/v2.2.2/sprint-28-202603/it/README.md`

## 验证

- [ ] GPMC 第三层 drill 页停留后不再退回 `/workbench`
- [ ] 顶号提示只在同一应用域内生效
- [ ] idle timeout 后可按 redirect 回原页面

## 完成标准

- [ ] 核心回归场景有明确执行方式
- [ ] IT 文档可直接作为发布前验收清单
