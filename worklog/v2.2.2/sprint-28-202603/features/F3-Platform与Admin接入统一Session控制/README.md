# F3: Platform与Admin接入统一Session控制

**优先级**: P0  
**状态**: READY

## 目标

在不改变 `platform` 与 `admin` 独立应用边界的前提下，让两端使用同一套 session 技术实现，同时彻底消除浏览器存储冲突和多套控制器并存问题。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 收口platform三套session控制器 | P0 | READY | F2/T03 |
| T02 | 修复platform登录回跳、公开路由与drill边界 | P0 | READY | T01 |
| T03 | admin接入共享session-core并切换namespace | P0 | READY | F2/T03 |
| T04 | 跨tab冲突、idle timeout与GPMC drill回归验证 | P0 | READY | T01,T02,T03 |

## 完成标准

- [ ] `platform` 只保留一个 session coordinator
- [ ] `admin` 与 `platform` 不再共享任何 `dts.session.*` key
- [ ] login redirect、public route、drill route 的行为在两端都一致且可预期
- [ ] GPMC 第三层停留后不再因 session 竞态退回 `/workbench`
