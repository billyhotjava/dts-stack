# T01: 定义session domain与存储协议

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

明确 `platform`、`admin`、`modern(迁移期)` 的 session domain、存储命名空间、广播事件和 redirect 规则，先统一协议，再统一实现。

## 技术设计

- 将应用域划分为：
  - `platform` 域：`dts-platform-webapp`，以及迁移期内仍需共享 platform 登录态的 `dts-analytics-webapp/modern`
  - `admin` 域：`dts-admin-webapp`
- 新协议采用独立 key 空间：
  - `dts.platform.session.*`
  - `dts.admin.session.*`
- 协议至少覆盖：
  - `sessionId`
  - `logoutTs`
  - `lastActivity`
  - `logoutReason`
  - `redirectIntent`
- redirect 规则必须区分 hash router 与 browser router，不允许再用 `pathname` 偷换完整 return URL。

## 影响范围

- `source/dts-platform-webapp/src/components/auth/**`
- `source/dts-admin-webapp/src/components/auth/**`
- `source/dts-analytics-webapp/modern/src/api/platformSession.ts`
- `source/dts-analytics-webapp/modern/src/routes/sessionGuard.ts`
- `docs/plans/**`

## 验证

- [ ] 协议文档能解释 `platform` 与 `admin` 为什么必须隔离 localStorage key
- [ ] 协议文档能覆盖多 tab、401 refresh、idle timeout、顶号、登录回跳五类场景

## 完成标准

- [ ] session domain 与 key 命名规则固定下来
- [ ] 广播事件与 redirect 语义有统一定义
