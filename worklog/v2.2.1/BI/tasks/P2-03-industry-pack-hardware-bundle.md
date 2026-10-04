# P2-03 行业包与硬件一体化交付

`status`: `done`  
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
  - 模板市场新增“行业包审计”面板（导入/导出记录、结果、requestId、详情筛选）。
- 已验证：
  - `mvn -f source/dts-analytics/pom.xml -DskipTests clean compile` 通过。
  - `pnpm -C source/dts-analytics-webapp/modern typecheck` 与 `build` 通过。
- 校验器语义增强（2026-02-15）：
  - `POST /api/screen-packs/validate` 新增语义校验：
    - `metadata.industry/hardwareProfile/deploymentMode` 合法性检查（与 presets 对齐）；
    - `deploymentMode` 为 `offline/isolated` 时，校验 `offlineBundle.enabled=true`；
    - `connectorTemplates` 逐项校验 `id/protocol`；
  - 增加更具体的推荐项（尤其是离线/隔离网下预置连接器模板）。
- 连接器任务桥接（2026-02-15）：
  - 后端新增 `POST /api/screen-packs/connectors/plan`：
    - 把 `connectorTemplates` 转换为 `dts-ingestion` 任务草案（含协议模式、调度、任务配置模板）；
    - 支持 `modbus-tcp/mqtt/opc-ua/jdbc` 预置任务类型；
    - 写入审计动作 `pack.connector-plan`。
  - 前端模板市场新增“采集任务草案”按钮：
    - 按连接器类型生成任务计划并导出 `dts-connector-plan.json`。
- 运维巡检链路（2026-02-15）：
  - 后端新增 `GET /api/screen-packs/ops/health`：
    - 输出模板数量、上架覆盖率、近期行业包审计失败率、离线连接器预置状态；
    - 返回统一 `summary + checks` 结果用于现场快速诊断。
  - 前端模板市场新增“运维巡检”按钮：
    - 按部署模式触发巡检并展示评分与检查项摘要。
- 连接器连通性探测（2026-02-16）：
  - 后端新增 `POST /api/screen-packs/connectors/probe`：
    - 基于连接器模板生成任务配置并提取目标端点（Modbus/MQTT/OPC-UA/JDBC）；
    - 执行 TCP 端口探测，返回 `summary(total/pass/warn/fail)` 与逐连接器明细；
    - 审计落地 `pack.connector-probe`。
  - 前端模板市场新增“连接器探测”按钮：
    - 支持选择连接器ID与超时时间；
    - 现场可快速确认“网关可达但端口不可达/服务未监听”类问题。
- 运行时依赖探测（2026-02-16）：
  - 后端新增 `POST /api/screen-packs/ops/runtime-probe`：
    - 对核心依赖服务做 TCP 探测（默认包含 analytics/platform/ingestion/db，可由环境变量覆盖）；
    - 支持自定义探测目标与超时时间，返回 `summary(total/pass/warn/fail)` 与逐目标明细；
    - 审计落地 `pack.ops-runtime-probe`。
  - 前端模板市场新增“运行时探测”按钮：
    - 现场一键执行运行时依赖探测并查看失败目标（主机、端口、错误信息）。
- 协议级探测增强（2026-02-16）：
  - `ops/runtime-probe` 从 TCP 升级为 `TCP + HTTP(S)` 双模式：
    - HTTP 探测支持 `protocol/path/url/expectedStatus`（如 `200-499`）；
    - 返回附加字段 `protocol/url/httpStatus`，可区分“端口可达但接口状态异常”。
    - 支持 `expectedBodyContains` 关键字断言，并输出 `bodyMatched/bodyPreview`，可识别“端口可达但返回网关错误页”。
  - 默认目标中 `analytics/platform/ingestion` 使用 HTTP 探测，`analytics-db` 使用 TCP 探测。
  - 前端运行时探测结果展示升级为 `协议 + 状态码 + URL + 错误信息`，便于现场排障。
- MQTT 协议探测补齐（2026-02-16）：
  - `ops/runtime-probe` 新增 `mqtt` 协议（CONNECT/CONNACK 探测）；
  - 可通过 `DTS_MQTT_HOST/DTS_MQTT_PORT` 自动注入默认 MQTT 目标；
  - 自定义探测目标支持 `protocol=mqtt`，用于边缘网关/Broker 快速连通性验证。
- 运维巡检接入真实探测（2026-02-16）：
  - `GET /api/screen-packs/ops/health` 新增 `includeRuntime=true`：
    - 在规则评分外，附加运行时依赖探测检查项 `runtime-connectivity`；
    - 输出关键依赖通过/告警/失败统计，避免“规则健康但服务不可达”盲区。
  - 模板市场“运维巡检”默认开启 `includeRuntime`，巡检结果直接包含真实连通性信息。
  - 模板市场“运行时探测”支持可选自定义目标输入（按行定义 `id,protocol,host,port,path,required,expectedStatus,expectedBodyContains`）。
- 收口完成（2026-02-22）：
  - MQTT 探测从 CONNECT/CONNACK 扩展到心跳探测（PINGREQ/PINGRESP）。
  - 运行时默认探测目标新增 `connector-task-status`（任务状态 API）。
  - 代码：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenIndustryPackResource.java`。
