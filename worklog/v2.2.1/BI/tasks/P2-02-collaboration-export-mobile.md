# P2-02 企业协作、导出与多端适配

`status`: `in-progress`  
`priority`: `P2`  
`inspiration`: `企业 BI 协作实践 + DataEase 交付便利性`

## 目标

从“单人设计工具”升级为“团队协作交付平台”。

## 子任务

1. 导出能力
- 导出 PNG/PDF/JSON；支持导入 JSON 复用。

2. 协作能力
- 评论/批注、变更记录、冲突提示（先轻协作，后实时协作）。

3. 多端适配
- PC/平板/手机布局策略（断点 + 缩放 + 组件可见性规则）。

4. 版本对比
- 支持版本差异查看（布局/组件/数据源变更）。

## 验收标准

- 大屏可导出 PNG/PDF 并保持视觉一致。
- 移动端可读，关键 KPI 与图表无遮挡。
- 两个版本可展示差异摘要。

## 风险与回滚

- 风险：多端布局规则过于复杂。  
- 回滚：先支持独立移动模板，不强制自动响应式。

## 实现记录（2026-02-14）

- 已完成首批交付能力：
  - 导出/导入：
    - `ScreenHeader` 新增 `JSON` 导出与 `JSON` 导入；
    - 新增 `PNG` 导出（实验路径：画布 DOM 转 PNG）；
    - 新增 `PDF` 导出（基于导出图像打开打印窗口）。
  - 多端适配：
    - 组件新增 `config.visibleOn`（`pc/tablet/mobile`）可见规则；
    - 属性面板新增多端可见配置；
    - 预览页与公开页按断点自动识别设备并过滤组件显示（`<=768: mobile`, `<=1200: tablet`, 其余 `pc`）。
    - 预览页与公开页新增 `device` 强制模式参数（`?device=pc|tablet|mobile`）；
    - 设计器预览入口新增设备模式切换（自动/PC/平板/手机），用于现场模拟多端效果。
    - 预览页/公开页设备切换逻辑已抽象复用，降低双页维护分叉风险。
  - 版本对比：
    - 后端新增 `GET /api/screens/{id}/versions/compare?fromVersionId=&toVersionId=`；
    - 返回组件与变量差异摘要（新增/移除数量、类型变化）；
    - 前端 `ScreenHeader` 增加“版本对比”入口与摘要弹窗。
  - 版本对比详情增强（2026-02-15）：
    - 后端 `versions/compare` 新增 `details` 字段：
      - `addedComponentIds/removedComponentIds`
      - `addedComponentTypes/removedComponentTypes`
      - `changedTypeComponents`
      - `addedVariableKeys/removedVariableKeys`
    - 前端新增“版本差异详情”面板（替代 `alert`）：
      - 展示摘要指标卡 + 明细列表，便于回滚前审查。
      - 支持差异结果一键导出 JSON 留档。
      - 支持一键复制差异摘要文本，便于微信群/工单同步。
    - 版本选择交互优化：
      - 去掉手工输入 `from,to` 的 prompt；
      - 改为“版本选择面板”下拉选择后再执行对比，降低现场误操作风险。
    - 版本回滚交互优化：
      - 去掉手工输入版本 ID 的 prompt；
      - 改为“版本回滚面板”下拉选择目标版本并确认回滚。
- 已验证：
  - `mvn -f source/dts-analytics/pom.xml -DskipTests clean compile` 通过。
  - `pnpm -C source/dts-analytics-webapp/modern typecheck` 与 `build` 通过。
- 轻协作评论/批注（2026-02-15）：
  - 后端新增评论接口（基于现有审计流，不新增表）：  
    - `GET /api/screens/{id}/comments`  
    - `GET /api/screens/{id}/comments/changes?sinceId=&limit=`（增量同步）  
    - `POST /api/screens/{id}/comments`  
    - `POST /api/screens/{id}/comments/{commentId}/resolve`  
    - `POST /api/screens/{id}/comments/{commentId}/reopen`
  - 前端新增“协作批注中心”面板：
    - `ScreenHeader` 增加“协作”入口；
    - 支持按组件添加评论、查看状态、标记已解决/重新打开；
    - 新增轻量冲突提示（草稿更新时间漂移检测）。
- 协作面板可用性增强（2026-02-15）：
    - 新增自动轮询刷新（可开关，默认 15 秒）；
    - 支持配置轮询间隔（5-120 秒）；
    - 轮询刷新走静默模式，不打断当前编辑与评论输入。
    - 轮询升级为增量同步（基于 `sinceId/cursor`）：
      - 常规轮询优先拉取变更评论并合并本地列表；
      - 发生窗口丢失时后端返回 `fullReload=true`，前端自动全量回补；
    - 兼容降级：增量接口不可用时自动回退到全量拉取。
- 准实时协作补强（2026-02-16）：
  - 后端新增长轮询接口：`GET /api/screens/{id}/comments/live?sinceId=&limit=&waitMs=`
    - 在 `waitMs` 窗口内等待评论变更（新增/解决/重开），无变化则超时返回；
    - 输出协议与 `comments/changes` 保持一致（`cursor/fullReload/rows`）。
  - 前端协作面板新增“实时长轮询”开关（默认开启）：
    - 开启后优先走长轮询链路，评论变更可在秒级同步；
    - 失败时自动降级到现有增量轮询，保证现场稳定性。
- 流式协作补强（2026-02-16）：
  - 后端新增 SSE 流接口：`GET /api/screens/{id}/comments/stream?sinceId=&limit=&durationSec=&waitMs=`
    - 输出 `ready/comment-change/heartbeat/stream-end` 事件；
    - 以固定窗口保持长连接，结束后前端可按最新 cursor 自动续连。
  - 前端协作面板新增 “SSE实时流” 开关：
    - 浏览器支持时优先走 SSE；
    - SSE 出错自动回退到增量轮询链路，确保不丢评论更新；
    - 浏览器不支持 SSE 时自动提示并降级轮询。
- 编辑锁冲突防护（2026-02-15）：
  - 后端新增编辑锁表与接口：
    - 表：`analytics_screen_edit_lock`
    - `GET /api/screens/{id}/edit-lock`
    - `POST /api/screens/{id}/edit-lock/acquire`
    - `POST /api/screens/{id}/edit-lock/heartbeat`
    - `POST /api/screens/{id}/edit-lock/release`
  - 后端在 `screen.update / screen.publish / screen.rollback / screen.delete` 增加锁冲突保护：
    - 被其他人占锁时返回 `409` + `code=SCREEN_EDIT_LOCKED`。
  - 前端新增“编辑锁”面板与状态提示：
    - 自动申请锁、心跳续期、离开页面自动释放；
    - 被他人占锁时禁用“保存/发布”并显示占用人提示；
    - 对 `409 SCREEN_EDIT_LOCKED` 统一弹窗与面板引导处理。
- 编辑锁接管能力增强：
  - `acquire` 接口支持 `forceTakeover=true`；
  - 强制接管要求 `MANAGE` 权限；
  - 编辑锁面板新增“强制接管”按钮。
- 导出合规前置校验（2026-02-15）：
  - 后端新增 `POST /api/screens/{id}/export-prepare`：
    - 校验 `read` 权限；
    - 接入合规策略 `exportApprovalRequired`；
    - 合规受限时返回 `403 + code=SCREEN_EXPORT_APPROVAL_REQUIRED`；
    - 记录导出准备/拒绝审计日志（`screen.export.prepare` / `screen.export.denied`）。
  - 后端新增 `POST /api/screens/{id}/export-report`：
    - 前端在导出成功/失败/回退后回传结果；
    - 审计落地 `screen.export.success / screen.export.failed / screen.export.fallback`。
  - 前端 `JSON/PNG/PDF` 导出接入导出前置校验；
    - 策略拒绝时给出可读提示，不再静默失败；
    - 保留现有浏览器端导出路径作为渲染兜底。
  - 前端新增统一导出页 `/screens/{id}/export`：
    - PNG/PDF 从运行态页面导出（非编辑器 DOM），减少样式偏差；
    - 支持 `format/mode/device/delayMs` 参数；
    - 导出页会再次执行 `export-prepare`，保证直连导出链路也受策略约束。
    - 导出页按合规策略渲染水印（`watermarkEnabled/watermarkText`）。
    - 未显式指定 `device` 时按当前视口自动判定导出设备模式（PC/平板/手机）。
    - 导出一致性增强（2026-02-16）：
      - `export-prepare` 返回本次导出的冻结 `screenSpec` 快照（可选 `includeScreenSpec`）；
      - 返回 `requestedMode/resolvedMode/specDigest`，支持导出回放与一致性审计；
      - 导出页优先使用该快照渲染，避免“prepare 到导出执行窗口”内的草稿漂移。
    - 导出审计补强：
      - `export-report` 记录 `resolvedMode`，可区分请求模式与实际导出模式（例如 published 回退 draft）。
      - `export-report` 记录 `specDigest`，实现“导出准备快照 -> 导出结果”链路追踪。
    - 新增导出失败回退链路：
      - PNG：跨域资源导致截图失败时自动打开预览页回退；
      - PDF：自动切换到 DOM 打印回退模式；
      - 页面提供“重试导出/打开预览页/PDF 打印回退”手工操作按钮。
    - 导出完成后回传后端导出结果（成功/失败/回退），便于现场追踪。
- 组件级并发合并与冲突可视化（2026-02-15）：
  - 后端 `screen.update` 增加组件级并发合并策略：
    - 新增 `_conflict` 元数据协议（`baseUpdatedAt/baseScreen/baseComponents/baseVariables`）；
    - 非重叠变更自动三方合并（组件、变量、标量字段）；
    - 重叠变更返回 `409` + `code=SCREEN_UPDATE_CONFLICT` + `componentIds/fields`。
  - 前端保存链路接入冲突协议：
    - 保存时自动携带基线快照冲突元数据；
    - 保存成功后同步刷新本地 baseline，避免连续误报冲突。
  - 前端新增“并发冲突面板”：
    - 显示冲突字段与冲突组件；
    - 支持一键选中冲突组件定位；
    - 支持一键重载最新草稿继续编辑。
- 待继续：
  - 目前已支持长轮询 + SSE 准实时协作，后续可补 WebSocket 双向协作态（输入中/光标态/presence）。
  - PNG/PDF 仍是浏览器侧轻实现，后续可升级为服务端一致性渲染导出。
- 协作态 presence/typing 补齐（2026-02-17）：
  - 后端新增轻量在线协作接口（内存态 + TTL 清理）：
    - `GET /api/screens/{id}/collaboration/presence`
    - `POST /api/screens/{id}/collaboration/presence/heartbeat`
    - `POST /api/screens/{id}/collaboration/presence/leave`
  - 接口输出会话级在线成员信息：`displayName/componentId/typing/idleSeconds/mine`，用于前端展示“谁在线、谁在输入、正在看哪个组件”。
  - 前端协作面板新增“在线协作态”区块：
    - 实时展示在线人数、输入中成员、会话标签；
    - 面板打开后自动心跳，输入评论时自动上报 typing 状态；
    - 面板关闭或离开时主动 `leave`，降低在线状态残留。
- 在线协作态 SSE 增强（2026-02-17）：
  - 后端新增 `GET /api/screens/{id}/collaboration/presence/stream`（SSE）：
    - 事件：`ready/presence-change/heartbeat/stream-end`；
    - 支持 `sessionId/ttlSeconds/durationSec/waitMs` 参数；
    - 在线状态变更时推送 `presence-change`，无变化时发送心跳。
  - `GET /collaboration/presence` 增加 `sessionId` 参数，确保“mine”会话标识稳定。
  - 前端协作面板新增“在线态SSE”开关：
    - 开启后在线成员优先走 SSE 秒级同步；
    - 失败自动回退一次快照拉取并重连，不中断批注流程。
- Presence 会话稳定性增强（2026-02-19）：
  - 前端 `sessionId` 改为 `sessionStorage` 持久化（同一浏览器标签页内稳定复用）；
  - 刷新页面后保持同一协作会话身份，减少“我自己被识别为新会话成员”的抖动。
