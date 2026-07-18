# T02：实现兼容路由与 planId 透传

- **状态**：READY
- **优先级**：P0
- **依赖**：T01、F3、F5、F6 目标页面可用
- **影响模块**：static/dynamic routes、redirect、breadcrumbs、专业页面 returnTo

## 目标

把旧建模、规划、主题域、模型和 dbt 深链安全映射到新页面，并让 `planId` 在跨模块跳转和返回中保持。

## 实施内容

1. 建立旧 route -> 新 route、参数和默认 Tab 映射表。
2. 对可识别旧计划 ID 先解析 canonical planId，再一次性 redirect。
3. 防止 redirect 循环、开放重定向和未经授权的 planId 注入。
4. 专业页面使用受控 `returnTo` 返回计划详情或工作台。
5. 记录旧 route 命中量，为后续移除提供证据。

## 验收标准

- 所有登记旧深链最多一次 redirect；
- planId、modelId 和 target Tab 不丢失；
- 未授权/不存在计划进入明确 403/404；
- 浏览器前进后退不形成循环；
- 调用审计能区分菜单点击和外部深链。

## 验证证据

- route mapping tests；
- redirect loop/security tests；
- Chrome 95 deep-link/back-forward smoke；
- 旧 route 审计样例。
