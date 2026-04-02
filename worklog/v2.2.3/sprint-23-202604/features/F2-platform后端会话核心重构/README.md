# F2: platform 后端会话核心重构

**优先级**: P0
**状态**: READY

## 目标
把 platform 后端从 opaque token successor-refresh 机制切到浏览器作用域的 cookie session 机制。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 登录链路切换到 cookie session 与 `session/current` | P0 | READY | F1/T01 |
| T02 | 续租机制改为 renew-in-place | P0 | READY | F1/T02 |
| T03 | Security filter、registry 与审计链路收敛 | P0 | READY | T01,T02,F1/T03 |

## 完成标准
- [ ] 登录成功后只依赖服务端 session cookie 建立登录态
- [ ] 续租不再创建 successor session，不再把旧 session 当作并发冲突
- [ ] 后端错误码、header、审计和查询接口完全对齐新状态机
