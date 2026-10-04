# T27: Copilot 插槽（ActivityBar icon + 占位面板 + 接口契约）

**优先级**: P1
**状态**: READY
**依赖**: T12

## 目标

为未来 AI 能力（NL2SQL / SQL 解释 / 优化建议）预留架构位置，本 Sprint 仅渲染占位，但把**接口契约（TypeScript 类型 + 后端 API 形状）**定死，避免未来对接需要改骨架。

## 技术设计

### UI 占位

- ActivityBar 底部添加 🤖 icon（高级模式显示，简洁模式不显示）
- 点击展开 SidePanel 显示占位面板：
  ```
  ┌─────────────────────────┐
  │  🤖 SQL Copilot         │
  │                         │
  │   敬请期待              │
  │                         │
  │   即将支持：            │
  │   · 自然语言转 SQL      │
  │   · SQL 解释与优化建议  │
  │   · 智能错误修复        │
  │                         │
  └─────────────────────────┘
  ```

### 接口契约（预定义）

**前端类型**：

```typescript
// copilot/types.ts
export interface CopilotRequest {
  mode: 'nl2sql' | 'explain' | 'optimize' | 'fix';
  sql?: string;
  naturalQuery?: string;
  schemaContext?: string;
  errorMessage?: string;
  datasourceId: string;
}

export interface CopilotResponse {
  sql?: string;
  explanation?: string;
  suggestions?: string[];
  confidence: number;
  modelVersion: string;
}

export interface CopilotProvider {
  chat(req: CopilotRequest): Promise<CopilotResponse>;
  stream?(req: CopilotRequest): AsyncIterator<Partial<CopilotResponse>>;
}
```

**后端契约**（预留端点，未实现）：

```
POST /api/sql/v2/copilot/chat
POST /api/sql/v2/copilot/stream  (SSE)
```

本 Sprint 只定义 DTO + 空 Controller 返回 501 Not Implemented。

### 组件

```
copilot/
  CopilotSlot.tsx        占位面板
  types.ts               契约定义
  provider.ts            Provider 接口
  README.md              给未来接入方的文档
```

### 未来接入指南（写入 copilot/README.md）

- 若对接 Claude Messages API：实现 `ClaudeCopilotProvider implements CopilotProvider`
- 若对接 Managed Agents：实现 `ManagedAgentCopilotProvider`
- 注入方式：通过 `CopilotProviderRegistry`（预留）
- 接入时不需动 ActivityBar、SidePanel、任何现有组件

## 影响范围

- 新增 `copilot/` 目录
- ActivityBar 加 icon（高级模式）
- `SqlIdeResource` 新增 2 个端点（返回 501）

## 验证

- [ ] 高级模式下看到 🤖 icon，点击展开占位面板
- [ ] 简洁模式下无 Copilot 入口
- [ ] 后端 `/copilot/chat` 返回 501
- [ ] TypeScript 类型定义编译通过
- [ ] `copilot/README.md` 有明确接入步骤

## 完成标准

- [ ] 占位 UI 完成
- [ ] 接口契约固化
- [ ] 未来对接无须改骨架（设计评审通过）
