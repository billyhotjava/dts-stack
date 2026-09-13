# T02：完成 Chrome 95 双起点端到端验收

- **状态**：READY
- **优先级**：P0
- **依赖**：F3-F7
- **影响模块**：dts-platform-webapp、Playwright、远程访问环境

## 目标

用 Chrome 95 验证业务起点、资产起点、模型中心、高级 dbt、失败修复、刷新恢复、旧路由和权限的真实客户旅程。

## 实施内容

1. 使用用户名密码自动登录和 SSH 可访问 IP 运行浏览器测试。
2. 完成 BUSINESS_FIRST：新建、基线、事实/维度、标准、发布成果。
3. 完成 ASSET_FIRST：Excel/ODS、剖析、映射、同一模型与发布流程。
4. 完成 dbt manifest 候选确认、compile/test/run、失败定位和恢复。
5. 验证旧深链、角色菜单、刷新、多 Tab、back/forward、错误状态，以及八阶段/九站/六 Tab 的单状态源映射。

## 验收标准

- IT-01 至 IT-16 的浏览器相关场景全部有结果；
- 页面无 Chrome 95 语法错误或关键布局遮挡；
- 每个主屏仅一个主要动作；
- planId 在刷新和跨模块返回中保持；
- 截图、trace、video/console 和失败重跑记录可复核。

## 验证证据

- Playwright HTML/JUnit report；
- 双起点和失败修复截图；
- browser console/network trace；
- 环境、commit、IP 和执行时间记录。
