# P2-03 行业包与硬件一体化交付

`status`: `in-progress`  
`priority`: `P2`  
`inspiration`: `DataEase 场景化模板 + DTS 硬件一体化策略`

## 目标

把大屏能力封装成可复制的行业交付包，增强软件+硬件整体价值。

## 子任务

1. 行业模板包
- 离散制造、能源、政企安全、园区运维等行业模板。

2. 硬件预置方案
- 工控机/边缘盒部署脚本、屏幕分辨率预置、离线包升级机制。

3. 数据连接模板
- 常见 PLC/MQTT/OPC-UA/数据库连接模板与指标映射。

4. 运维包
- 健康检查、告警、日志采集、故障快速诊断 SOP。

## 验收标准

- 新站点可通过标准包在 1 天内完成首屏上线。
- 弱网/隔离网环境可离线部署并稳定运行。

## 风险与回滚

- 风险：行业需求分化导致模板泛化失败。  
- 回滚：先做 2-3 个重点行业深模板，再逐步扩展。

## 实现记录（2026-02-15）

- 已完成行业包与硬件一体化首批能力：
  - 后端新增行业包预置查询接口：`GET /api/screen-packs/presets`
    - 行业预置、硬件预置、连接器模板、部署模式。
  - 后端新增行业包校验接口：`POST /api/screen-packs/validate`
    - 校验 packageType/templates/metadata 结构并返回 `errors/warnings/recommendations`。
  - 增强行业包导出 `POST /api/screen-packs/export`：
    - 包含 `metadata.industry/hardwareProfile/deploymentMode`；
    - 自动补充 `resolution/offlineBundle/opsRunbook/hardwarePreset/connectorTemplates`；
    - 审计日志记录行业与硬件信息。
  - 增强行业包导入 `POST /api/screen-packs/import`：
    - 支持 packageType 校验；
    - 导入时把行业/硬件标签注入模板 tags（`industry:*` / `hardware:*`）。
- 前端已打通配套调用：
  - `analyticsApi` 增加 `getScreenIndustryPackPresets` / `validateScreenIndustryPack`；
  - 模板市场行业包导入前先校验并提示；
  - 行业包导出时支持填写行业/硬件/部署模式与连接器类型。
- 已验证：
  - `mvn -f source/dts-analytics/pom.xml -DskipTests clean compile` 通过。
  - `pnpm -C source/dts-analytics-webapp/modern typecheck` 与 `build` 通过。
- 待继续：
  - 数据连接模板当前是元数据模板（plc/mqtt/opcua/postgresql），尚未联动具体采集任务生成器。
  - 运维包仍为静态 runbook 结构，后续可对接健康检查实际执行链路。
