# T05: CORS 配置统一收敛到 Traefik 层

**优先级**: P2
**状态**: READY
**依赖**: 无

## 问题

当前 CORS 配置分散在三层：
1. **Traefik 动态配置**：`services/dts-proxy/dynamic/` 下的 CORS 中间件（正则匹配 origin + credentials=true）
2. **Spring Boot**：`application.yml` 的 `jhipster.cors` 配置（platform 注释掉了，admin 未确认）
3. **docker-compose**：`JHIPSTER_CORS_ALLOW_CREDENTIALS: true` + origin 白名单环境变量

多层配置容易产生 double `Access-Control-Allow-Origin` header，浏览器会直接拒绝请求。

## 技术设计

### 方案
1. Traefik 作为唯一的 CORS 处理层
2. Spring Boot 侧完全关闭 CORS 配置（已注释或删除）
3. docker-compose 中移除 `JHIPSTER_CORS_*` 环境变量

### 改动

| 文件 | 改动 |
|------|------|
| `services/dts-proxy/dynamic/*.yml` | 确认 CORS 中间件配置完整正确 |
| `dts-platform/application.yml` | 确认 `jhipster.cors` 保持注释/删除 |
| `dts-admin/application.yml` | 注释/删除 `jhipster.cors` 配置 |
| `docker-compose-app.yml` | 移除 `JHIPSTER_CORS_*` 环境变量 |
| `docker-compose.legacy.yml` | 同上 |

## 验证

- [ ] 跨域请求正常携带 credentials
- [ ] Response 只有一个 `Access-Control-Allow-Origin` header
- [ ] Preflight OPTIONS 请求返回正确的 CORS headers
- [ ] PKI 登录（可能涉及 KoalMiddleware 跨域请求）不受影响

## 影响范围

- 仅调整 CORS 配置来源
- 不改动任何业务逻辑或认证逻辑
