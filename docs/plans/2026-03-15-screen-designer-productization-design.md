# Screen Designer Productization Design

## 背景

对 `source/dts-analytics-webapp/modern/src/pages/screens` 与 `source/dts-analytics` 的静态扫描表明：

- 大屏设计器核心、模板资产中心、行业包导入导出、发布回滚、协作与导出能力已经不是占位页
- 当前最明显的缺口不在“有没有代码”，而在“市场闭环、产品化交互、插件安装闭环、测试覆盖”

具体表现为：

- `ScreenMarketplacePage` 与 `analyticsApi` 已声明 `/analytics/api/marketplace/*`，但后端未落地
- 模板/行业包相关页面存在较多 `window.prompt / alert / confirm`
- `screen-plugins` 目前仍以 demo manifest 为主，未与“市场安装”形成真实闭环
- `screens` 目录已有 82 个文件，但前端只有 3 个测试文件，后端也缺少 screen/template/pack/plugin 专项 IT

## 目标

在 `customer/2.2.1` 分支上完成一轮“大屏设计器产品化收口”：

1. 把组件/模板市场从前端壳页补成可访问、可安装、可验证的真实能力
2. 将模板资产中心、行业包、发布回滚等关键操作从工程化 prompt/alert 收口为可交付 UI
3. 让插件清单、市场安装、组件库可见性形成一致闭环
4. 为后续 Playwright 回归和客户交付补齐最基础的测试与文档基线

## 方案选择

### 方案 A：重做设计器内核

优点：

- 可以一次性重构信息架构和交互

缺点：

- 风险极高
- 与当前扫描结论不匹配，属于“推倒重来”
- 会打断已有模板、发布、协作、导出能力

### 方案 B：围绕“产品化缺口”做收口 Sprint

优点：

- 与当前真实问题对齐
- 复用现有设计器、模板、行业包内核
- 可以按后端闭环、前端交互、测试补齐拆成独立 task

缺点：

- 不会在这一期解决所有高级可视化与生态问题

### 方案 C：只补测试，不补功能闭环

优点：

- 改动小

缺点：

- `marketplace` 仍是 404 / 空实现
- 大量 prompt/alert 仍会拖累交付体验
- 测试只能覆盖“现状缺口”

## 选型

采用方案 B。

本期不重做设计器内核，而是把最明显、最影响交付的 4 类问题收口：

- 市场闭环
- 模板/行业包产品化交互
- 插件安装与可见性闭环
- 测试与回归基线

## 设计原则

### 1. 不重复造一个“第二套模板系统”

继续复用现有三层结构：

- 内置模板：`screenTemplates.ts`
- 模板资产：`/api/screen-templates`
- 行业包：`/api/screen-packs`

这期只补“怎么被更稳定地消费和验证”，不再引入第四套资产模型。

### 2. 市场先做“实例内资产仓”，不做真正社区平台

`marketplace` 第一阶段只要求：

- 后端接口真实存在
- 能浏览组件/模板资产
- 能执行安装/克隆/导入闭环
- 能反映已安装状态

不在这一期做：

- 外部社区源同步
- 远端审核流
- 多租户资产分发网络

### 3. 工程调试入口与客户交付入口分层

行业包导出、运维巡检、运行时探测这些能力仍保留，但前端不继续依赖原始 `prompt/alert`。应改为：

- Drawer / Modal 表单
- 结构化结果面板
- 明确的空态、加载态、失败态

### 4. 插件闭环优先于插件扩展

这一期不新增更多插件类型，重点解决：

- 市场安装后如何进入 `screen-plugins`
- 组件库如何感知安装结果
- 插件安装失败如何回显

### 5. 测试优先覆盖“真实闭环”，而不是 UI 细枝末节

优先补：

- `marketplace` 不是 404
- 模板资产与行业包链路可以走通
- 插件安装后组件库可见
- 发布/回滚/模板保存关键路径可回归

## 分模块设计

### Marketplace

后端新增最小 `MarketplaceResource` 与配套服务，支撑：

- 组件列表
- 模板列表
- 组件安装
- 模板安装

第一期建议采用“实例内资产仓”模型：

- 组件市场数据可先来自本地 manifest / 安装目录
- 模板市场数据可复用 `screen-templates` 并叠加“市场展示”字段

核心目标是消除前后端断层，而不是做完整开放平台。

### Template Gallery / Industry Pack

保留 `TemplateGallery` 的能力边界，但把高频流程 UI 化：

- 模板导入
- 行业包导入导出
- 运维巡检
- 运行时探测
- 模板上下架
- 模板版本恢复

这些动作需要从 `prompt/alert` 迁移到标准弹窗、表单和结果面板。

### Screen Header Productization

`ScreenHeader` 当前已经有很多能力，但交互仍偏工程工具。需要优先收口：

- 存为模板
- 发布
- 回滚
- 导出
- 分享
- 分析会话沉淀

原则是保留已有能力，不重做状态机，只替换为可交付 UI。

### Plugin Closure

`screen-plugins` 目前已具备协议、注册中心、边界门禁和 demo manifest。

这一期需要补的是：

- 市场安装如何写入可读取的安装清单
- 后端 `screen-plugins` 如何返回安装后插件，而不只是 demo
- 前端组件库如何区分“已安装 / 未安装 / 版本变化”

### Test & Verification

补两层测试：

- 后端：`ScreenTemplateResource` / `ScreenIndustryPackResource` / `ScreenPluginResource` / `MarketplaceResource` 的集成测试
- 前端：`TemplateGallery` / `ScreenMarketplacePage` / 关键 action helper 的单测或组件测试

在此基础上，把 analytics screen 相关关键路径放进既有 Web E2E 体系。

## 本期不做

- 不重写 `ScreenDesignerPage` 核心编辑器内核
- 不新增一批新的图表类型或地图/3D 能力
- 不把 `marketplace` 做成真正外部社区平台
- 不重构行业包协议本身
- 不做移动端大屏重新设计

## 输出物

- `docs/plans/2026-03-15-screen-designer-productization-plan.md`
- `worklog/v2.2.1/sprint-8/README.md`
- `worklog/v2.2.1/sprint-8/tasks/*.md`
- `worklog/v2.2.1/sprint-8/it/README.md`
