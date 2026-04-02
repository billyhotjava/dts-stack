# F1: 浏览器作用域会话模型

**优先级**: P0
**状态**: IN_PROGRESS

## 目标
明确“浏览器是会话主体、tab 只是视图”的统一语义，为后续后端、前端、proxy、analytics 改造提供单一规则。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 浏览器作用域会话规则定稿 | P0 | IN_PROGRESS | - |
| T02 | `portal_sessions` 数据模型与索引迁移 | P0 | READY | T01 |
| T03 | 会话状态机与接管语义统一 | P0 | READY | T01 |

## 完成标准
- [ ] 浏览器、tab、session、takeover、idle timeout 的术语和边界明确定义
- [ ] `portal_sessions` 新字段、唯一约束、迁移策略明确
- [ ] 所有退出原因统一映射到可观测的服务端状态和前端 reason code
