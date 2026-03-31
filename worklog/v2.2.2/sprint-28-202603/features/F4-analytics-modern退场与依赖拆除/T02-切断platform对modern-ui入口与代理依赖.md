# T02: 切断platform对modern UI入口与代理依赖

**优先级**: P0  
**状态**: READY  
**依赖**: T01,F1/T02

## 目标

让 `dts-platform-webapp` 不再把 `modern` 当成一个仍需代理和兼容的 UI 目标，为后续删除 `modern` 前端清障。

## 技术设计

- 清理 platform 中仍指向 `dts-analytics-webapp-modern` 的 dev/proxy/兼容入口。
- 确保 analytics 页面、分享页、drill 页都由 platform 自身路由承载。
- 对仍需兼容的旧链接提供 platform 内重定向，而不是继续依赖 `modern` UI。

## 影响范围

- `source/dts-platform-webapp/vite.config.ts`
- `source/dts-platform-webapp/src/routes/**`
- `source/dts-platform-webapp/src/analytics/**`

## 验证

- [ ] platform 本地开发不再依赖 `modern` UI 服务
- [ ] 旧 analytics 入口进入 platform canonical route

## 完成标准

- [ ] platform 与 modern UI 解耦
- [ ] `modern` 不再是 platform 的前端依赖
