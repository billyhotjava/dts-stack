# Sprint-48: 前端页面驱动的数据中台重构治理

**时间**: 2026-06
**状态**: DONE
**类型**: Architecture Governance + Implementation（skills + 页面矩阵 + 后续编码约束 + 首批页面整改）
**目标**: 固化 DTS 企业级数据中台后续前端重构的执行规则：以现有前端页面为第一事实源，尽量不新增菜单或页面，通过页面、按钮、组件、路由和接口契约把功能点串成真实产品闭环。

## 背景

Sprint-45 已完成数据中台 UI 产品化整改，Sprint-46 已完成唯一工作台和个人定制，后续继续开发时需要防止再次出现页面割裂、重复菜单、假按钮、客户 demo 场景内置、后台能力强但前台产品弱的问题。

本 sprint 先把后续所有 DTS 前端实现必须遵守的 skills、矩阵和验收规则固化下来，再按矩阵启动首批页面整改。后续新建或修改页面时，先过页面能力矩阵，再拆 feature/task，再用 TDD 和浏览器验证闭环。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | DTS 前端重构 skills 固化 | 3 | DONE |
| F2 | 现有页面能力矩阵基线 | 4 | DONE |
| F3 | 菜单路由收敛规则 | 3 | DONE |
| F4 | TDD 与 Chrome95 验收基线 | 3 | DONE |

## 完成标准

- [x] DTS 专属 skills 已创建并通过 `quick_validate.py`
- [x] 已形成页面能力矩阵，覆盖当前 portal 菜单主干
- [x] 已形成按钮/组件矩阵，明确每个可见控件必须有状态和闭环
- [x] 已定义菜单/路由收敛策略，默认复用/合并/跳转，不新增页面
- [x] 已定义后续编码的 TDD、source-contract、Playwright、Chrome95 验收入口
- [x] 已完成首批整改：清理内置 demo 场景夹具、工作台偏好 API 默认关闭并保留本地偏好

## 关键决策

- 前端页面是 DTS 产品重构的第一事实源；后台能力只作为页面闭环的契约补充。
- 默认不新增菜单或页面。除非页面矩阵证明没有现有承载面，否则优先收敛到已有入口。
- 所有客户可见功能必须落到页面、按钮、组件、状态、接口或外部交接点。
- 客户业务场景不内置为产品 demo；统一标记为现场定义或由客户配置。
- Chrome 95 兼容是硬约束，后续 UI 不引入需要新浏览器能力的交互方案。
- 后端未升级前，个人工作台保存使用浏览器本地偏好；服务端偏好 API 通过 `WEBAPP_ENABLE_WORKBENCH_PREFERENCE_API=true` 显式开启。

## 资产

- 页面矩阵: `assets/page-capability-matrix.md`
- 按钮组件矩阵: `assets/button-component-matrix.md`
- 重构规则: `assets/dts-frontend-refactor-rules.md`
- 验收证据: `it/README.md`
