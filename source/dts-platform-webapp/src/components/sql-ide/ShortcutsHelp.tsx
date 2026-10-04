import { Modal, Typography } from "antd";
import type { FC } from "react";

const SHORTCUTS: Array<{ key: string; action: string }> = [
  { key: "Ctrl+Enter", action: "执行当前 SQL (或选中块)" },
  { key: "Ctrl+Shift+Enter", action: "执行并在新 Tab 展示结果" },
  { key: "Ctrl+Alt+F", action: "格式化整段 SQL" },
  { key: "Ctrl+S", action: "保存为 Saved Query" },
  { key: "F8", action: "跳到下一个错误" },
  { key: "Ctrl+`", action: "切换底部面板" },
];

export const ShortcutsHelp: FC<{ open: boolean; onClose: () => void }> = ({ open, onClose }) => (
  <Modal open={open} onCancel={onClose} footer={null} title="快捷键" width={520}>
    <table style={{ width: "100%" }}>
      <tbody>
        {SHORTCUTS.map((s) => (
          <tr key={s.key}>
            <td style={{ padding: "6px 8px" }}>
              <Typography.Text code>{s.key}</Typography.Text>
            </td>
            <td style={{ padding: "6px 8px" }}>{s.action}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </Modal>
);
