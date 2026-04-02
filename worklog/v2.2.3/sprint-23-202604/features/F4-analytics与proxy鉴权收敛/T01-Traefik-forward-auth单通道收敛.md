# T01: Traefik forward-auth 单通道收敛

**优先级**: P0
**状态**: READY
**依赖**: F2/T01

## 目标
统一 analytics 流量入口，只通过 platform 已认证的浏览器 session 获取身份。

## 技术设计
- 审核并收敛 `/bi/api/**`、screen 预览、dashboard、card 的 Traefik 路由和 middleware 绑定
- 让 forward-auth 直接读取 platform cookie session 并注入统一 `X-DTS-*` 头
- 统一处理未认证、已失效、被接管三类失败响应，避免 proxy 和业务服务各自定义
- 明确代理缓存、header 透传、cookie path/domain 策略，避免 `/bi` 路由绕开鉴权

## 影响范围
- `services/dts-proxy/dynamic/traefik-dynamic.yml`
- `docker-compose*.yml`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/`
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts`

## 验证
- [ ] `/bi/api/screens/:id`、dashboard、card 请求都命中同一 forward-auth 逻辑
- [ ] 未登录或已失效时，proxy 返回统一错误语义
- [ ] cookie session 在 `/bi` 路径下可正确透传

## 完成标准
- [ ] analytics 入口不存在旁路认证路径
- [ ] 代理层对会话失败原因有统一输出
