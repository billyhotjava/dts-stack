# T02: 生产构建 strip 所有 console.log(Authorization) 及敏感日志

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

消除生产 bundle 中打印 Authorization 头、X-Portal-Access-Token 与响应体的 console 语句，避免凭据/PII 经 F12、浏览器扩展、远程 debug 外泄（OWASP A09）。构建期统一移除，生产 profile 不残留任何敏感日志。

## TDD 测试先行（RED）

- 新增源码契约测试 `apiClient.logging.source-contract.test.ts`（vitest）：断言 `dts-platform-webapp/src/api/apiClient.ts` 不存在裸 `console.log(... config ...)` / `console.log(... res.data ...)`，敏感打印必须经 `import.meta.env.DEV` 守卫或脱敏 logger。
- 新增构建产物测试 `prod-bundle.no-secret-log.test.ts`：对生产 `vite build` 产物 grep `Authorization`、`X-Portal-Access-Token`、`console.log` 关键片段，命中即 FAIL。
- 新增 `secretLogger.test.ts`：logger 对 `Authorization` 等敏感字段输出 `***` 掩码。
- 运行确认 FAIL（当前 `apiClient.ts:320,331` 明文打印）。

## 技术设计（GREEN）

- 修改 `dts-platform-webapp/src/api/apiClient.ts:320,331`：移除整体 config/response 打印，改为脱敏 logger（屏蔽 `Authorization` / `X-Portal-Access-Token`）并以 `import.meta.env.DEV` 包裹。
- 配置 `dts-platform-webapp/vite.config.ts` 与 `dts-admin-webapp/vite.config.ts`：生产构建 `esbuild.drop=['console','debugger']`（或 terser `drop_console`），编译期 strip。
- 抽取共用脱敏 logger 工具，替换零散 `console.log`。
- admin webapp 同步排查并 strip 同类敏感打印。

## 影响范围

- `source/dts-platform-webapp/src/api/apiClient.ts:320,331`（改既有 symbol，先 gitnexus_impact）
- `source/dts-platform-webapp/vite.config.ts`
- `source/dts-admin-webapp/vite.config.ts`
- `source/dts-admin-webapp/src/api/apiClient.ts`

## 验证

- [ ] 生产构建产物 grep 不到 `Authorization` / `X-Portal-Access-Token` 日志输出。
- [ ] 源码内敏感打印均经 DEV 守卫或脱敏 logger。
- [ ] 生产 bundle 无残留 `console.log`/`debugger`。

## 完成标准

- [ ] 生产构建零敏感日志泄露，凭据不落 console。
