import { type FC } from "react";

export const CopilotSlot: FC = () => (
  <div
    data-testid="sqlide-copilot-slot"
    style={{
      padding: 24,
      color: "var(--ant-color-text-secondary)",
      textAlign: "center",
    }}
  >
    <div style={{ fontSize: 32, marginBottom: 12 }}>🤖</div>
    <div style={{ fontWeight: 600, marginBottom: 8, color: "var(--ant-color-text)" }}>
      SQL Copilot
    </div>
    <div style={{ marginBottom: 16 }}>敬请期待</div>
    <div style={{ fontSize: 12, lineHeight: 1.7 }}>
      即将支持：<br />
      · 自然语言转 SQL<br />
      · SQL 解释与优化建议<br />
      · 智能错误修复
    </div>
  </div>
);
