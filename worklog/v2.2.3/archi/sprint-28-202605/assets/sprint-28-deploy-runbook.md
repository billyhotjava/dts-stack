# Sprint-28 部署 Runbook

## 升级前检查

- [ ] 备份当前 docker-compose / k8s manifests / values.yaml 中所有 `DTS_ADMIN_*`、`DTS_PLATFORM_*` env 配置
- [ ] 确认所有内部服务版本一致(platform / ingestion / analytics 同时升级,避免 token 模型不一致)
- [ ] 确认 `DTS_ADMIN_SERVICE_TOKEN`(或同等共享 secret)已设置且非空,所有相关服务能访问到

## 升级路径(渐进式,推荐)

### Stage 1:零代码变更上线

直接部署 Sprint-28 镜像,**无需修改任何 env**。所有旧 env(`DTS_ADMIN_SERVICE_TOKEN` / `DTS_PLATFORM_SERVICE_TOKEN` 等)通过 fallback 链继续工作。

预期效果:
- platform 出站调 admin 时 X-DTS-Service header 自动修正为 `dts-platform`(F1 副产品 bug 修复)
- platform 入站对 ingestion / analytics 强校验 token,但因 fallback 链每个 service 都拿到相同 sharedSecret,**仍正常**
- analytics 调 platform 第一次必须带 token(从未带过)→ 通过 `DTS_ADMIN_SERVICE_TOKEN` fallback 自动取到,**正常**

**风险点**:如果运维之前没设 `DTS_ADMIN_SERVICE_TOKEN`(空字符串 fallback),analytics 调 platform 立即 403。**部署前必查**。

### Stage 2(可选,Sprint-28 后期):每对独立 secret

```bash
SECRET_FROM_INGESTION=$(openssl rand -hex 32)
SECRET_FROM_ANALYTICS=$(openssl rand -hex 32)

# platform
export DTS_INBOUND_FROM_INGESTION=$SECRET_FROM_INGESTION
export DTS_INBOUND_FROM_ANALYTICS=$SECRET_FROM_ANALYTICS

# ingestion
export DTS_INGESTION_TO_PLATFORM=$SECRET_FROM_INGESTION

# analytics
export DTS_ANALYTICS_TO_PLATFORM=$SECRET_FROM_ANALYTICS
```

滚动重启所有相关服务。每对 secret 独立,任一泄露只影响该对调用。

### Stage 3(Sprint-29):移除 deprecated env

按 `env-migration-matrix.md` 中的 deprecated 时间线,Sprint-29 起移除旧 env 的 fallback,只保留新命名。

## 升级后验证

### 静态 smoke(可重复跑)

```bash
PLATFORM_BASE=http://dts-platform:8081 \
DATA_SOURCE_ID=<some-data-source-uuid> \
INGESTION_TOKEN=<DTS_INBOUND_FROM_INGESTION value> \
bash worklog/v2.2.3/sprint-28-202605/it/scripts/service-auth-smoke.sh
```

期望输出:5 PASS / 0 FAIL。

### 真链路 E2E(原 403 修复证据)

1. 在 dts-platform-webapp 创建一个 PostgreSQL 类型的数据源
2. 进入数据接入 → 新建入湖任务,选用上一步创建的数据源,运行
3. 期望:任务运行成功,无 `获取平台数据源失败: 403` 错误

### 日志检查

```bash
# platform 日志中不应有 service_auth_denied(若 service 间 token 配置正确)
kubectl logs deployment/dts-platform | grep service_auth_denied | wc -l
# 期望:0

# 若有,看 reason 字段定位(token_missing / token_mismatch / service_unknown)
kubectl logs deployment/dts-platform | grep service_auth_denied | head -5
```

## 故障处理

### 现象:某 service 调 platform 持续 403

排查顺序:
1. 检查 platform 启动日志中 `dts.platform.inbound.service-auth.trusted-services` Map 是否含该 service
2. 检查发起方的 outbound token env 是否设置
3. curl 直接验证(用上面 smoke 脚本)
4. 临时回滚:开启 `DTS_PLATFORM_LEGACY_HEADER_ONLY_MODE=true`(production 慎用)+ 紧急联系 SRE

### 现象:启动日志出现 SECURITY 级 WARN

```
WARN  SECURITY: dts.platform.inbound.service-auth.legacy-header-only-mode=true detected in PRODUCTION profile.
```

立即把 `DTS_PLATFORM_LEGACY_HEADER_ONLY_MODE` 设为 `false`(默认值)并重启。

### 现象:platform 出站调 admin 失败

如果错误日志含 `X-DTS-Service: dts-platform`(F1 修复后的正确值),且 admin 端拒绝 — 检查 admin 侧 `auditing.ingest.service-tokens` 或 admin filter 白名单是否含 `dts-platform`。这通常是 admin 侧配置问题,不是 platform 侧。

## 回滚

如需完全回滚到 Sprint-27 行为(不推荐):

1. 部署 Sprint-27 版本镜像
2. 不需要改 env(Sprint-27 用的就是这套 `DTS_ADMIN_*` env)
3. 安全回退:Sprint-27 的越权面将再次暴露,务必尽快重新升级
