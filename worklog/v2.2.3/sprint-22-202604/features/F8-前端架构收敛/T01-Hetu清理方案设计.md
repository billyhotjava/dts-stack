# T01: Hetu 清理方案设计

**优先级**: P2
**状态**: READY
**依赖**: 无

## 目标
设计 Hetu BI 引擎的完整清理方案，移除所有相关配置和代码引用。

## 技术设计

### 现状
- Hetu BI 引擎已决定废弃，但相关配置和代码仍然存在
- 涉及点:
  - Traefik 动态路由: `/dashboards/*`, `/screen`, `/system`, `/tdv`, `/account` 代理到 `host.docker.internal:7778`
  - Docker Compose: `HETU_UPSTREAM_IP`, `extra_hosts` 配置
  - `.env`: `HETU_*` 相关环境变量
  - 前端路由: platform-webapp 中的 Hetu 嵌入页面
  - dts-analytics 服务: 需确认与 Hetu 的关系

### 设计要点
1. **影响范围全量梳理**:
   - Traefik 配置（`traefik-dynamic.yml`）: 所有 hetu 相关路由
   - Docker Compose 文件: extra_hosts, 环境变量
   - `.env` 文件: HETU_* 变量
   - 前端代码: 引用 `/dashboards` 路径的组件和路由
   - 后端代码: 任何引用 Hetu 的 API 或服务
2. **清理顺序**: 先停用路由 -> 清理前端引用 -> 清理后端引用 -> 清理配置
3. **回退方案**: 确保清理后可快速恢复（保留配置注释或 git tag）
4. **dts-analytics 定位**: 确认 analytics 服务在 Hetu 移除后的角色

## 影响范围
- `services/dts-proxy/dynamic/traefik-dynamic.yml`: Hetu 路由规则
- `docker-compose.yml` / `docker-compose-app.yml`: extra_hosts, env
- `.env`: HETU_* 变量
- `source/dts-platform-webapp/`: Hetu 嵌入页面/路由
- `source/dts-analytics/`: 确认服务定位

## 验证
- [ ] 包含所有 Hetu 引用点的完整清单（grep 结果）
- [ ] 包含清理步骤和顺序
- [ ] 包含回退方案
- [ ] 确认 dts-analytics 服务独立于 Hetu

## 完成标准
- [ ] 产出完整的 Hetu 清理方案文档，含逐文件修改说明
