# P2-02 企业协作、导出与多端适配

`status`: `done`  
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
- WebSocket 双向协作态补强（2026-02-20）：
  - 后端新增 WebSocket 协作通道：`/api/screens/{id}/collaboration/ws`；
  - 新增 `ScreenCollaborationRealtimeService`：
    - 处理 `presence.heartbeat/presence.update/presence.leave` 双向消息；
    - 广播 `presence-change` 事件，包含 `typing/componentId/cursorId/selectedCount`；
    - 新增 `comment-change` 广播，评论新增/解决/重开可秒级推送到在线协作者；
    - 内置 TTL 清理与会话上限修剪，防止僵尸会话累积。
  - 前端协作面板新增“WebSocket协作态”开关：
    - 在线态优先走 WS，失败自动重连并回退至现有 SSE/轮询；
    - 实时同步输入中、选中组件数量、定位组件态；
    - WS连接成功时，评论链路自动停用 SSE/长轮询，断开后自动回退，避免重复拉取。
  - 协作冲突可视化增强：
    - 在线成员标签新增“定位”按钮，可一键选中对应组件；
    - 新增“冲突热点”区（多人聚焦同一组件/与我选中重叠）并支持一键定位，降低并行编辑冲突成本。
- 服务端一致性导出补强（2026-02-20）：
  - 后端新增 `POST /api/screens/{id}/export-render`：
    - 服务端统一渲染 PNG/PDF（含水印策略）；
    - 返回 `X-Screen-Spec-Digest/X-Screen-Resolved-Mode/X-Screen-Render-Engine`。
  - 前端导出页改为“服务端优先，浏览器回退”：
    - 优先调用 `renderScreenExport` 下载一致性导出文件；
    - 服务端失败时自动切回原浏览器导出链路，不影响现场交付。
  - 编辑器头部导出链路补齐一致性审计：
    - `PNG/PDF` 先走 `export-prepare`，再走 `export-render`；
    - 服务端失败自动回退导出页，并回传 `success/fallback/failed` 审计结果；
    - 透传 `requestId/specDigest/resolvedMode`，便于导出链路追踪与现场排障。
- 导出清晰度链路收口（2026-02-22）：
  - 后端 `ScreenResource` 补齐 `pixelRatio` 请求参数解析与透传，修复 `export-render` 仍按旧签名调用导致的全量编译失败；
  - 后端响应新增 `X-Screen-Render-Pixel-Ratio`，审计日志同步记录 `pixelRatio`；
  - 前端 `analyticsApi.renderScreenExport` 扩展 `pixelRatio` 入参与响应解析；
  - 编辑器头部导出与导出专页统一倍率策略（PNG 默认高倍率，PDF 默认中倍率，并按设备模式微调），服务端失败回退链路保持不变。
  - `export-render` 追加设备模式过滤：按组件 `config.visibleOn` 过滤非当前设备组件，保证导出与预览设备视图一致；
  - 响应补充 `X-Screen-Device-Mode/X-Screen-Hidden-By-Device`，前端导出页可提示“按设备模式隐藏组件数量”。
- 服务端导出渲染保真升级（2026-02-22）：
  - `ScreenServerRenderExportService` 新增组件级绘制逻辑，不再仅输出“标题+类型占位框”；
  - 已支持 `line/bar/scatter/pie/funnel` 图表轮廓、`table/scroll-board` 表格、`number-card` 指标卡、`title/markdown/datetime/marquee/countdown/carousel/tab-switcher` 文本类组件、`map-chart` 区域条带摘要；
  - 渲染顺序改为按 `zIndex` 统一排序，并尊重组件 `visible=false`（导出不再误绘隐藏组件）；
  - 服务端渲染默认启用 `java.awt.headless=true`，修复无桌面环境下 `Graphics2D` 连接 X11 失败的问题；
  - 在无有效配置时自动降级到类型提示，保证导出稳定性；
  - 当前保真级别为“启发式高保真”，后续可继续升级到图表像素级一致渲染内核。
  - 新增 `ScreenServerRenderExportServiceTest`，覆盖 PNG/PDF 产物与倍率基础校验。
- 编辑头部菜单兼容性修复（2026-02-20）：
  - 将 `ScreenHeader` 的分组菜单从 `details/summary` 改为受控弹层（button + panel）；
  - 增加菜单外点击关闭、`ESC` 关闭，避免旧浏览器下菜单状态异常残留；
  - 修复 Chrome 95 下“变量/缓存观测/合规”等菜单点击后无面板弹出的兼容性问题。
- 编辑页头部收口与交互稳态（2026-02-21）：
  - 下拉菜单点击从捕获阶段关闭改为“执行动作后关闭菜单”，规避旧浏览器事件时序导致的面板未打开问题；
  - “分享链接”并入治理菜单，保留 `预览/发布/保存` 作为主按钮，减少头部按钮密度；
  - 主按钮文案去除表情符号，提升商务场景下界面整洁度与一致性。
- WS 评论写入补齐（2026-02-21）：
  - 后端 `ScreenCollaborationRealtimeService` 新增 `comment.create` 指令：
    - 支持基于当前会话用户的权限校验（`EDIT`）后直接落库评论审计并广播 `comment-change`；
    - 新增 `comment-created` 回执事件与带 `requestId` 的错误事件，便于前端请求级匹配。
  - 前端 `ScreenCollaborationPanel` 新增“WS优先提交评论”：
    - WS 可用时优先发 `comment.create`，收到 `comment-created` 回执后落地；
    - WS 失败/超时自动回退 HTTP 创建接口，保证现场可用性。
  - 握手链路补齐 `X-DTS-Roles` 透传，确保 WS 写入权限判断与 HTTP 口径一致。
- WS 评论状态写入补齐（2026-02-22）：
  - 后端协作实时服务新增 `comment.resolve/comment.reopen` 指令，支持在线状态变更与广播；
  - 新增 `comment-updated` 回执事件（含 `requestId`），前端可按请求级匹配完成态；
  - 前端“标记已解决/重新打开”改为 WS 优先，失败自动回退 HTTP，形成统一写入通道。
- 服务端导出引擎收口（2026-02-22）：
  - 服务端渲染引擎版本升级为 `server-heuristic-v2`；
  - 补齐高级组件绘制覆盖：`gauge-chart/radar-chart/scroll-ranking/progress-bar/percent-pond/water-level/digital-flop/flyline-chart/filter-*/image/video/iframe/border-box/decoration/container/shape`；
  - 新增导出单测 `renderPng_supportsAdvancedComponentTypes`，确保高级组件组合场景可稳定出图。
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
- Presence 协作上下文增强（2026-02-20）：
  - 心跳接口新增 `selectedIds` 上报，后端在线态返回 `selectedCount/selectionPreview`；
  - 协作面板在线成员标签新增“当前选中组件数量”信息，便于多人并行编辑避让；
  - 兼容旧字段协议，不影响已有在线态展示链路。
- Presence 内存态稳定性增强（2026-02-20）：
  - `upsertPresence` 增加写入前 TTL 清理，减少“长期无快照查询时的僵尸会话”残留；
  - 增加在线会话数上限修剪（按最久未活跃优先淘汰），避免异常会话增长拖垮内存；
  - 仅影响内存态治理，不改变前端协议与已有在线态展示行为。
