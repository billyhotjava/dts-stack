# DTS 前端页面驱动重构规则

**目标产品**: 企业级数据中台
**执行策略**: 现有前端页面优先，尽量不增加菜单或页面，从页面提炼功能说明和实现任务。

## 默认 skill 顺序

1. `dts-page-capability-audit`
2. `dts-frontend-feature-matrix`
3. `dts-menu-route-convergence`
4. `dts-customer-language-polish`
5. `superpowers:test-driven-development`
6. `dts-chrome95-regression`
7. `superpowers:verification-before-completion`

## 开工前规则

- 先找现有菜单、路由、页面组件、source-contract 测试。
- 能复用现有页面就复用；能合并就合并；老路径用兼容跳转。
- 新增菜单必须有页面矩阵证明：现有入口无法承载。
- 页面 feature 必须能落到按钮、组件、表格列、弹窗、接口或外部交接点。
- 客户业务场景不写死到产品中，统一通过配置或现场定义。

## 编码规则

- 改函数、类、方法前按项目 AGENTS.md 走 GitNexus impact。
- 先写 source-contract/unit test，并确认 RED。
- 再做最小实现，并确认 GREEN。
- 只改当前 task 需要的页面、组件、路由或契约。
- 不做顺手重构，不改无关菜单，不清理用户未要求的历史变更。

## 菜单路由规则

| 情况 | 策略 |
|------|------|
| 同一用户同一任务多个入口 | 保留一个 canonical route |
| 老链接可能已被客户使用 | 保留 redirect |
| 页面只是技术配置 | 放在 admin/system 或 ops，不进客户主导航 |
| 页面没有真实 handler/API/state | 隐藏、禁用并说明原因，或拆任务实现 |
| BI/metrics/语义跨服务 | 明确 frame/外链/原生页，不模糊表达 |

## 客户语言规则

- 页面标题说业务能力，不说后台模块。
- 按钮说用户动作，不说实现动作。
- 空态说下一步配置，不塞假数据。
- 错误态说依赖服务和重试方式。
- Worklog 先写客户问题和可见变化，再写技术文件。

## Chrome 95 规则

- 不使用 CSS container query、`:has()`、无 fallback 的 `structuredClone`、`toSorted`、`Object.groupBy`。
- 表格必须固定关键列宽，操作列不被挤压。
- Drawer/Modal 必须在 1366x768 下可用。
- 长中文按钮不能因 loading 或 disabled 造成布局跳动。
- 可选后端接口 404 不能阻塞登录或首页可用性。

## 完成前证据

每个实现型 task 必须记录：

- RED 测试命令和失败原因
- GREEN 测试命令和通过结果
- `pnpm build` 或模块构建结果
- 浏览器路线、截图、console/network 结果
- 不能执行的验证项和原因
