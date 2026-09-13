# F4: 系统监控告警与业务仪表盘

**优先级**: P0（协议 2.3.2.9）
**状态**: DRAFT（依赖 F0 交付基线）

## 目标
运维人员能在平台外的统一告警渠道收到「服务不可用 / 入湖任务连续失败 / 磁盘水位 / 审计写入中断 / 登录暴力破解」等真实告警，并在只读看板上看到平台核心业务指标，而不是只有一堆 JVM 曲线。

**闭合缺口**: P0-11（自定义告警规则缺失）、M09 P1（业务级监控仪表盘）。
**现状**（账本#8、#9）：actuator 已暴露 prometheus 端点，但全仓无 `prometheus.yml`、无 `rule_files`、无 Alertmanager、无 Grafana；`AlertResource` 是 BI 卡片告警，与系统监控无关。

## 范围边界
- **不在 dts-platform-webapp 内重画监控页**（ADR-99-07，domain-dts B：默认不新增菜单/页面）。平台内只保留一个跳转入口。
- **不做告警的多级通知升级策略**（依赖值班/审批组织模型，客户未定）。本 Feature 做到「规则触发 → Alertmanager 收到 → 按接收器分发」，接收器配置项留给现场。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| 编排 | `docker-compose-app.yml` 新增 `dts-prometheus`、`dts-alertmanager`、`dts-grafana` | 均为单实例（本 Sprint 不做 HA，P0-12 属 Sprint-101）；挂载只读配置卷 |
| 配置 | `services/dts-prometheus/prometheus.yml` | `scrape_configs` 覆盖 dts-platform / dts-admin / dts-ingestion / dts-metrics 的 `/management/prometheus`；`rule_files: ["rules/*.yml"]`；`alerting.alertmanagers` |
| 配置 | `services/dts-prometheus/rules/dts-platform.yml` | 告警规则组（见 T02 清单） |
| 配置 | `services/dts-alertmanager/alertmanager.yml` | 路由 + 抑制 + 接收器（webhook 占位，现场替换） |
| 指标 | dts-platform 新增业务指标（Micrometer） | `dts_ingestion_task_failed_total{taskType}`、`dts_audit_write_failed_total`、`dts_login_locked_total`、`dts_sensitive_scan_duration_seconds`、`dts_baseline_check_status{checkKey}` |
| UI | 数据安全页/运维页新增「监控看板」外链按钮 | 跳 Grafana，不内嵌 iframe（CSP 与登录态问题） |

## UI/UX 规格

- **入口与导航**: 既有运维/系统页新增一个外链按钮，文案「打开监控看板」，`target=_blank`。无新增菜单、无新增路由。
- **四态**: 该按钮无数据态；Grafana 不可达时按钮仍可点（由浏览器显示连接失败），**不做健康探测遮罩**（避免为一个外链引入探测逻辑）。
- **Grafana 看板**（随离线包交付的 JSON）:
  ```
  ┌ DTS 平台总览 ───────────────────────────────┐
  │ 服务健康(4)  入湖成功率  审计写入速率        │
  │ ┌────────┐ ┌──────────┐ ┌────────────────┐ │
  │ │ UP/DOWN│ │ 24h 曲线 │ │ 5m 速率        │ │
  │ └────────┘ └──────────┘ └────────────────┘ │
  │ 近 24h 告警列表 / 基线检查失败项            │
  └──────────────────────────────────────────────┘
  ```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 监控栈编排与指标采集接入 | P0 | DRAFT | F0/T01 |
| T02 | 告警规则集与非功能预算 | P0 | DRAFT | T01 |
| T03 | 业务指标埋点与只读看板 | P0 | DRAFT | T01 |

## Definition of Ready
- [x] 契约已钉死（compose 服务 + 配置文件 + 指标名）
- [x] 竖切片已画通（应用埋点 → actuator → prometheus → 规则 → alertmanager → 接收器）
- [x] UI 落点已命名（一个外链按钮 + Grafana 看板 JSON）
- [ ] 依赖 F0
- [x] 验收可验证（人为制造故障 → 告警真实到达）

## 完成标准
- [ ] 人为停掉 dts-ingestion → 5 分钟内 Alertmanager 收到 `ServiceDown` 告警
- [ ] 人为让入湖任务连续失败 → 触发 `IngestionTaskFailureBurst`
- [ ] 告警规则含抑制策略，单次故障不产生告警风暴
- [ ] Grafana 看板显示业务指标（非仅 JVM）
- [ ] `assets/nfr-budget.md` 与 `assets/runbook.md` 落地（Gate G1/G4）
