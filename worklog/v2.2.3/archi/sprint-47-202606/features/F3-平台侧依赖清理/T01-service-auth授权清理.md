# T01: metrics service-auth 授权清理

**优先级**: P1
**状态**: READY
**依赖**: F2 切断稳定

## 目标
移除/收紧 dts-platform 中仅为 dts-metrics 设的服务间认证授权，**不误伤**其他内部服务调用。

## 技术设计
- 审 `security/MetricsInternalAccess.java` + `security/ServiceDependencyAuthenticationFilter.java`：定位 metrics 服务（`X-DTS-Service: dts-metrics` 类）的授权条目/白名单。
- 确认这些 internal API（catalog/dbt/release/permission）仍被**其他**合法调用方（如 ingestion、内部任务）使用 → 只移除"dts-metrics"这个服务身份的授权，保留端点与其他授权。
- 若 `MetricsInternalAccess` 是 metrics 专用 → 整体移除；若共享 → 仅摘 metrics 条目。

## 影响范围
- `dts-platform`：`MetricsInternalAccess`、`ServiceDependencyAuthenticationFilter`（+ 配置）。

## 验证
- [ ] 移除后其他内部调用方鉴权不受影响（既有安全测试不破）。
- [ ] 无 dts-metrics 服务身份残留授权。

## 完成标准
- [ ] 精确清理、其他内部调用不受损、`clean test` 通过。
