# T01: 可登录 Playwright 环境与账号夹具

**优先级**: P0
**状态**: READY
**依赖**: F1/T01

## 目标

建立可登录的浏览器验证环境，解决当前 smoke 被 `#/auth/login` 和 `platform.dts.local` DNS 解析失败阻断的问题。

## 技术设计

- 明确 dev proxy 的 `platform.dts.local` 解析方式。
- 准备可用测试账号或保存认证态。
- 提供 `.env` 或脚本说明，不把凭证写入仓库。
- 失败时输出登录页、DNS、接口状态的明确诊断。

## 影响范围

- `source/dts-platform-webapp`
- Playwright 脚本或 MCP smoke 流程
- `it/README.md`

## 验证

- [ ] Playwright 打开工作台时 URL 停留在 `/workbench?journey=e2e-data-product`。
- [ ] `/platform/api/session/status` 不再 DNS 失败。
- [ ] 登录失败时输出截图和 console 日志路径。

## 完成标准

- [ ] Browser smoke 不再只能证明登录阻断。
- [ ] 环境修复步骤可被其他开发者复用。
