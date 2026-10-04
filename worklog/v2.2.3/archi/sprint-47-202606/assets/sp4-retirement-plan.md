# SP-4 dts-metrics 退役清单与足迹

**产出**: 2026-06-16
**原则**: verify-first → 灰度切断 → 保留回退窗口 → 先归档再删；每步可回退。

## 退役足迹清单（已勘察）

### 部署（docker-compose-app.yml + docker-compose.legacy.yml）
- `dts-metrics` 服务（L810+），image `dts-metrics:1.0.0`，port `8084`，env `DTS_METRICS_*`。
- traefik 路由：
  - `dts-metrics-api`：`Host(HOST_PLATFORM_UI) && PathPrefix(/api/metrics)`，prio 260，中间件 platform-forward-auth/cors/security-headers，loadbalancer 8084。
  - `dts-metrics-ui`：`PathPrefix(/metrics)`，prio 255，service=dts-metrics-api。

### 平台侧（dts-platform）
- `security/MetricsInternalAccess.java` + `security/ServiceDependencyAuthenticationFilter.java`：metrics 服务的 X-DTS-Service service-auth 授权（dts-metrics → 平台 internal API）。
- `config/metrics/DtsMetricsCapabilityProperties.java` + `application.yml`：metrics 能力/iframe href/服务地址配置。

### 前端（dts-platform-webapp）
- iframe `MetricsServiceFrame` + `metricsServiceRoutes.ts` —— **由 SP-3 (Sprint-44) F3-T02 移除**，本 sprint 只确认其已完成。

### 代码与文档
- `source/dts-metrics`（后端）、`source/dts-metrics-webapp`（前端）。
- `opmanager/README.md`、`opmanager/docs/2026-05-19-opmanager-handoff.md` 引用。
- sprint-35b 硬化分支（`feat/sprint-35b-dts-metrics-hardening`）：随退役作废，归档说明。

### 数据
- v2.2.3 基线 dts-metrics = 内存态 ConcurrentHashMap（无持久化），**无生产数据需迁移**。
- sprint-35b 的 JPA 持久化未进 v2.2.3、不在退役范围（已作废）。

## 退役步骤（gated）
1. **gate（F1）**：SP-3 平价 + 路由切原生确认；依赖审计（仅 iframe 消费 + 平台 internal 调用方向是 dts-metrics→平台，退役不影响平台）；数据处置确认（无数据）。
2. **灰度切断（F2）**：停 `/metrics` UI 路由 → 观察 → 停 `/api/metrics` → 保留 dts-metrics 服务镜像 N 天回退窗口。
3. **下线（F2）**：回退窗口无异常 → compose 移除 `dts-metrics` 服务 + 两条 traefik 路由。
4. **平台清理（F3）**：移除 metrics service-auth 授权 + 配置（精确定位，勿误伤其他内部调用）。
5. **归档（F4）**：`source/dts-metrics` + `dts-metrics-webapp` 移出构建（保留 git 历史，加 ARCHIVED 说明）；opmanager 文档更新；sprint-35b 分支归档。

## 回退预案
- 回退窗口内任一异常：恢复两条 traefik 路由 + 重启 `dts-metrics` 服务（镜像未删）→ 即恢复。
- 平台 service-auth/config 清理（F3）置于回退窗口之后，确保切断稳定再清理。

## 验收
- 退役后：语义建模全走平台原生页；`/metrics`、`/api/metrics` 无 503（已重定向/收敛）；平台无对 dts-metrics 的残留授权/配置；grep `dts-metrics` 仅余归档说明。
