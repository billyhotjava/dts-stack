# P3-10 定时快照与报告 API

`status`: `done` (frontend)
`priority`: `P3`
`sprint`: `Sprint 3 - 企业能力`
`inspiration`: `DataEase(POST /snapshot 自动截图) + 企业报告分发需求`

## 目标

提供服务端驱动的大屏截图 API 和定时快照任务，支持自动化报告生成与邮件/钉钉分发。

## 当前状态

- 已有 `ScreenExportPage.tsx` 支持手动 PNG/PDF 导出。
- 后端已有 `prepareScreenExport` API 和截图服务基础。
- 缺少：定时调度、批量截图、分发通道。

## 子任务

### 1. 快照 API 标准化

**后端 API**:
```
POST /api/screens/{id}/snapshot
Body: {
  format: 'png' | 'pdf',
  mode: 'published' | 'draft',
  device: 'pc' | 'tablet' | 'mobile',
  pixelRatio: 1 | 2 | 3,
  delay: 2000,              // JS 执行等待时间 (ms)
  variables?: Record<string, string>,  // 可注入全局变量
  watermark?: string,
}
Response: {
  taskId: string,
  status: 'pending' | 'running' | 'done' | 'error',
  resultUrl?: string,       // 完成后的下载 URL
}
```

**任务轮询**:
```
GET /api/screens/snapshot-tasks/{taskId}
```

### 2. 截图服务增强

**文件**: 后端截图服务

- 使用 headless Chrome（Playwright / Puppeteer）渲染大屏页面。
- 支持注入全局变量（通过 URL 参数 `var_{key}=value`）。
- 支持水印覆盖。
- 超时控制：最大 60 秒。
- 结果存储到文件系统或对象存储。

### 3. 定时快照任务

**后端新增**:
```
POST /api/screens/{id}/snapshot-schedules
Body: {
  name: string,
  cron: string,             // cron 表达式
  format: 'png' | 'pdf',
  device: 'pc',
  variables?: Record<string, string>,
  enabled: boolean,
  distribution?: {
    type: 'email' | 'webhook',
    recipients?: string[],   // 邮箱列表
    webhookUrl?: string,     // 钉钉/飞书 webhook
  },
}
```

- 基于 Spring `@Scheduled` 或 Quartz 调度。
- 每次执行生成快照 + 分发。
- 执行历史记录保留（最近 100 次）。

### 4. 前端管理界面

**文件**: `ScreenHeader.tsx` 或新增 `ScreenSnapshotPanel.tsx`

- 快照计划列表（增删改、启停）。
- 执行历史（时间、状态、结果预览/下载）。
- 手动触发"立即截图"按钮。

### 5. 分发通道

**Phase 1（MVP）**:
- 邮件分发：截图作为附件发送。
- Webhook：将截图 URL 推送到钉钉/飞书群机器人。

**Phase 2（后续）**:
- 企业微信通道。
- 自定义 HTTP 回调。

## Chrome 95 兼容性

- 截图服务使用服务端 headless Chrome（版本可控），与客户端浏览器无关。
- 前端管理界面为常规表单，Chrome 95 ✅。

## 验收标准

- API 调用可获取大屏 PNG/PDF 截图。
- 定时任务按 cron 表达式自动执行。
- 邮件/Webhook 正确分发截图。
- 执行历史可查询。

## 风险与回滚

- 风险：headless Chrome 资源消耗大。
- 回滚：限制并发截图任务数（max 3），排队等待。
