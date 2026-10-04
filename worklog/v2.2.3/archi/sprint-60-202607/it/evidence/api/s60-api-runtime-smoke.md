# Sprint-60 API 部署冒烟证据

日期：2026-07-14

容器重建后，未携带会话访问新写接口得到认证响应而非 404，说明 Traefik → `dts-platform` 路由已加载：

| 请求 | HTTP |
|---|---:|
| `GET https://bi.yuzhicloud.com/api/modeling/vnext/model-specs/nonexistent/release-gate` | 401 |
| `POST https://bi.yuzhicloud.com/api/modeling/vnext/runs/nonexistent/callback` | 401 |

写接口仍由 `CATALOG_MAINTAINERS` 权限保护；带租户和有效会话的业务响应由后端资源测试与 Testcontainers 集成测试覆盖。
