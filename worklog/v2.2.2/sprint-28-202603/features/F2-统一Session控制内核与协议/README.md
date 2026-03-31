# F2: 统一Session控制内核与协议

**优先级**: P0  
**状态**: READY

## 目标

为 `dts-platform-webapp`、`dts-admin-webapp` 和迁移期内的 `dts-analytics-webapp/modern` 定义同构的 session 协议与共享控制内核，彻底结束“每个入口各写一套 refresh/logout/redirect”。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 定义session domain、storage namespace与广播协议 | P0 | READY | - |
| T02 | 抽离共享session-core包与应用适配器契约 | P0 | READY | T01 |
| T03 | 统一refresh、logout、idle timeout与redirect状态机 | P0 | READY | T01,T02 |
| T04 | 设计旧key兼容迁移、观测埋点与回滚策略 | P1 | READY | T01,T02,T03 |

## 完成标准

- [ ] `platform` 与 `admin` 共享同一套 session 协议定义
- [ ] refresh single-flight、logout reason、redirect builder 只保留一套实现
- [ ] 浏览器存储 key 明确按 domain 隔离，不再复用 `dts.session.*`
- [ ] 对旧 key 和旧 bundle 有明确兼容与回滚方案
