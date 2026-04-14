# SQL IDE Copilot 接入指南

本目录是 SQL IDE 的 AI Copilot 插槽（placeholder）。本 Sprint (F6/T27) 只落地契约，不实现。

## 契约

实现 `CopilotProvider` 接口（见 `types.ts`）即可接入：

- `chat(req: CopilotRequest): Promise<CopilotResponse>` —— 单次请求
- `stream(req: CopilotRequest)` —— 可选，流式返回

### 四种模式

| mode | 输入 | 输出 |
|---|---|---|
| `nl2sql` | `naturalQuery`（用户自然语言）+ `schemaContext` | `sql` + `confidence` |
| `explain` | `sql` | `explanation` 散文 |
| `optimize` | `sql` + `schemaContext` | `sql` 优化版 + `explanation` + `suggestions[]` |
| `fix` | `sql` + `errorMessage` | `sql` 修正版 + `explanation` |

## 实现 Provider

### 选项 A：Claude Messages API

```typescript
import type { CopilotProvider, CopilotRequest, CopilotResponse } from "./types";

export class ClaudeMessagesProvider implements CopilotProvider {
  readonly name = "claude-messages";
  readonly supportedModes = ["nl2sql", "explain", "optimize", "fix"] as const;

  async chat(req: CopilotRequest): Promise<CopilotResponse> {
    const systemPrompt = buildSystemPrompt(req.mode, req.schemaContext);
    const userMessage = buildUserMessage(req);
    const resp = await fetch("/api/copilot/messages", {
      method: "POST",
      body: JSON.stringify({ system: systemPrompt, messages: [userMessage] }),
    });
    const json = await resp.json();
    return parseResponse(json);
  }
}
```

后端需要新增 `/api/copilot/*` 端点代理 Anthropic Messages API，并注入项目的 audit + rate-limit + security-rewriter。

### 选项 B：Anthropic Managed Agents

使用 `@anthropic-ai/sdk` 的 `client.agents.create/sessions.create/events.send`。Managed Agents 内置工具（bash、file、search），适合多轮工作流。

### 前端挂载

`CopilotSlot.tsx` 读取默认 provider。引入时：

1. 定义 `CopilotProviderRegistry` 单例（可用 Zustand 或普通模块级 Map）
2. 应用启动时 `registry.register(new ClaudeMessagesProvider(...))`
3. CopilotSlot 内部 `registry.default()?.chat(...)`

## 安全与合规

- 所有 Copilot 调用必须走后端代理（不直接从浏览器调 Anthropic API，避免 key 泄露）
- 生成的 SQL 在执行前仍需要过 `SecuritySqlRewriter`（不能绕过密级/部门过滤）
- Copilot 调用和结果应该走 `SqlIdeAuditActions` 审计（待新增常量）

## 延后事项

- 纯占位 UI（见 `CopilotSlot.tsx`）
- 简洁模式下 Copilot icon 隐藏（F6/T26 已经做）
- 真正的 provider 实现放到未来 Sprint
