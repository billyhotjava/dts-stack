# F1-业务消费工作台页面与菜单入口

**状态**: DONE
**优先级**: P0
**目标**: 把 Sprint-39 F5 的业务消费能力从后端/文档能力补成可点、可看、可演示的服务中心页面。

## Task 列表

| ID | Task | 状态 | 交付物 |
|----|------|------|--------|
| T01 | 菜单与路由接入 | DONE | `/services/consumption` 静态路由、动态菜单解析、菜单 seed、角色默认项 |
| T02 | 业务消费工作台页面 | DONE | 接入 `/api/golden-chains` 真实链路状态，展示报表数据集、指标入口、数据 API、数据产品、权限一致、客户验收包 |
| T03 | UI 契约与构建验证 | DONE | source-contract、ops route 回归、前端生产构建、whitespace 检查 |

## 设计约束

- 面向传统行业结构化数据客户，用业务语言组织，不把底层实现作为默认入口。
- 页面以 `/api/golden-chains` 为主链路状态源，复用已有报表、指标、API、数据产品和运维页面。
- 保持菜单 seed 和角色默认项同步，避免页面可构建但客户侧菜单不可见。

## 验证

- `pnpm exec tsx src/pages/services/BusinessConsumptionPage.source-contract.test.ts`
- `pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`
- `pnpm build`
- `git diff --check`
