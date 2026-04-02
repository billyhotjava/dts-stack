# F3: 前端会话运行时重构

**优先级**: P0
**状态**: READY

## 目标
删除前端 portal token 驱动的登录态管理，改为服务端 session 探针驱动的运行时模型，并修复闪屏与慢刷问题。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 移除前端 portal token 持久化与固定 refresh | P0 | READY | F2/T01 |
| T02 | 登录守卫、登录页与重定向状态机重写 | P0 | READY | T01,F1/T03 |
| T03 | 同浏览器多 tab 协调与闪屏治理 | P0 | READY | T01,T02 |

## 完成标准
- [ ] 前端不再把 portal session token 存在 `localStorage` 或同类浏览器可见存储中
- [ ] 登录页和路由守卫基于服务端 session 状态判断，不再发生 login/preview 循环跳转
- [ ] 同浏览器双 tab 共享会话但不再触发冲突提示
