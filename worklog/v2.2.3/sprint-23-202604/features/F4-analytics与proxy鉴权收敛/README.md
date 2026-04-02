# F4: analytics 与 proxy 鉴权收敛

**优先级**: P0
**状态**: READY

## 目标
把 analytics 的身份来源收敛到 platform forward-auth 单通道，移除 bearer fallback 造成的双轨语义。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Traefik forward-auth 单通道收敛 | P0 | READY | F2/T01 |
| T02 | analytics 关闭 bearer fallback | P0 | READY | T01 |
| T03 | metabase session bridge 隔离与 screen 兼容验证 | P0 | READY | T01,T02 |

## 完成标准
- [ ] analytics 入口只接受 proxy 注入的受信头
- [ ] bearer fallback 关闭后 screen、dashboard、card 仍正常工作
- [ ] analytics 内部 session 只作为兼容层，不再反向影响 platform 会话
