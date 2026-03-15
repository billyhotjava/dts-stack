# Sprint-8: 大屏设计器产品化与资产市场收口

## 目标

在 `customer/2.2.1` 分支上，围绕 `dts-analytics` 与 `dts-analytics-webapp/modern` 完成一轮大屏设计器产品化收口：

1. 补齐 `marketplace` 的真实后端与前端闭环
2. 将模板资产中心、行业包、发布/回滚等高频动作从工程化 prompt/alert 收口为可交付 UI
3. 打通插件安装、插件清单、组件库可见性的真实闭环
4. 为大屏设计器补齐后端 IT、前端测试与 Web 自动化基线

## 范围

### 后端

- `source/dts-analytics`
- 大屏相关 REST / service / asset storage / plugin registry

### 前端

- `source/dts-analytics-webapp/modern/src/pages/screens`
- `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`

### 自动化与文档

- `tests/web-e2e`
- `docs/plans`
- `worklog/v2.2.1/sprint-8`

## Task 列表

### 批次一：市场闭环（SD-001 ~ SD-002）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| SD-001 | Marketplace 后端最小闭环 | 后端 | 2天 | TODO |
| SD-002 | Marketplace 前端收口与安装体验 | 前端 | 1.5天 | TODO |

### 批次二：模板与操作产品化（SD-003 ~ SD-004）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| SD-003 | TemplateGallery 去 prompt/alert，改结构化弹窗流 | 前端 | 2天 | TODO |
| SD-004 | ScreenHeader 高价值动作产品化 | 前端 | 2天 | TODO |

### 批次三：插件闭环（SD-005）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| SD-005 | 插件安装与可见性闭环 | 前后端 | 2天 | TODO |

### 批次四：测试补齐（SD-006 ~ SD-008）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| SD-006 | 大屏资产后端集成测试 | 后端测试 | 2天 | TODO |
| SD-007 | screens 模块前端测试扩充 | 前端测试 | 1.5天 | TODO |
| SD-008 | Playwright smoke 与 runbook 收口 | 自动化 | 1天 | TODO |

## 本 Sprint 不做

- 不重写 `ScreenDesignerPage` 编辑器内核
- 不新增一大批图表/地图/3D 组件
- 不把 marketplace 做成真正外部社区平台
- 不重构行业包协议本身
- 不做移动端大屏重设计

## 集成测试

`it/` 目录存放本 Sprint 的验证说明，重点覆盖：

- marketplace 不再 404
- 模板/行业包/插件关键路径可走通
- 发布/回滚/模板保存等动作具备可自动化回归入口
- analytics screens 相关改动可以进入既有 Playwright 基线

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：

- 空白 = 未开始
- WIP = 进行中
- DONE = 已完成
- BLOCK = 阻塞
