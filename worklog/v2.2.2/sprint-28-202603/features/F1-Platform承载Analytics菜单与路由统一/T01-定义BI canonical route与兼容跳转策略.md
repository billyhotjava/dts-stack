# T01: 定义BI canonical route与兼容跳转策略

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

为 BI 页面建立统一正式路由，并为旧 `/analytics/*` 提供兼容跳转层。

## 技术设计

- 在 `dts-platform-webapp` 中定义 `/dashboard/bi/*` 作为 BI canonical route；
- 保留 `/analytics/*` 到 canonical route 的 redirect compatibility；
- 公共分享页保持独立路由，不并入业务壳。

## 影响范围

- `source/dts-platform-webapp/src/routes/sections/**`
- `source/dts-platform-webapp/src/constants/**`
- `source/dts-platform-webapp/src/analytics/**`

## 验证

- [ ] `/dashboard/bi/*` 路由可达
- [ ] `/analytics/*` 旧路径正确跳转
- [ ] 公开分享页不走登录壳

## 完成标准

- [ ] canonical route 清晰且可配置
- [ ] 兼容跳转不引入循环或死链
