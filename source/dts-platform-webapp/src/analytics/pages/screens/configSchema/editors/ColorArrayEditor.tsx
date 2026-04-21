import React, { useEffect, useState } from 'react';
import { Button, ColorPicker, Input, message } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';

export interface ColorArrayEditorProps {
  value: string[];
  onChange: (v: string[]) => void;
}

// 与 FieldEditor 里 isValidColor 保持一致的宽松校验。
const COLOR_HEX_RE = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;
const COLOR_FUNC_RE = /^(rgb|rgba|hsl|hsla)\s*\([^)]*\)\s*$/i;
const COLOR_VAR_RE = /^var\s*\(\s*--[\w-]+\s*(?:,[^)]*)?\)\s*$/;
const COLOR_KEYWORDS = new Set(['transparent', 'currentcolor']);
function isValidColor(text: string): boolean {
  const v = text.trim();
  if (!v) return false;
  return (
    COLOR_HEX_RE.test(v)
    || COLOR_FUNC_RE.test(v)
    || COLOR_VAR_RE.test(v)
    || COLOR_KEYWORDS.has(v.toLowerCase())
  );
}

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
    <span style={{ fontSize: 12 }}>{label}</span>
    <div style={{ maxWidth: '60%' }}>{children}</div>
  </div>
);

interface ColorSlotProps {
  idx: number;
  value: string;
  onCommit: (idx: number, next: string) => void;
  onRemove: (idx: number) => void;
}

const ColorSlot: React.FC<ColorSlotProps> = ({ idx, value, onCommit, onRemove }) => {
  const [draft, setDraft] = useState(value);
  useEffect(() => { setDraft(value); }, [value]);

  const commit = () => {
    const next = draft.trim();
    if (!next) {
      onCommit(idx, '');
      return;
    }
    if (isValidColor(next)) {
      onCommit(idx, next);
    } else {
      message.warning('颜色格式无效，已恢复上一次有效值');
      setDraft(value);
    }
  };

  return (
    <LabelRow label={`颜色 ${idx + 1}`}>
      <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
        <ColorPicker
          size="small"
          value={value}
          onChange={(_, hex) => onCommit(idx, hex)}
        />
        <Input
          size="small"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={commit}
          onPressEnter={commit}
          style={{ width: 100 }}
        />
        <Button
          size="small"
          type="text"
          icon={<DeleteOutlined />}
          onClick={() => onRemove(idx)}
          danger
        />
      </div>
    </LabelRow>
  );
};

const ColorArrayEditor: React.FC<ColorArrayEditorProps> = ({ value = [], onChange }) => {
  const updateAt = (idx: number, color: string) => {
    const next = [...value];
    next[idx] = color;
    onChange(next);
  };

  const removeAt = (idx: number) => {
    onChange(value.filter((_, i) => i !== idx));
  };

  const add = () => {
    onChange([...value, '#409eff']);
  };

  return (
    <div>
      {value.map((color, idx) => (
        // 使用 idx + color 作为 key 有助于"删除中间一项后其他项保持各自状态"
        <ColorSlot
          key={`${idx}-${color}`}
          idx={idx}
          value={color}
          onCommit={updateAt}
          onRemove={removeAt}
        />
      ))}
      <Button
        size="small"
        type="dashed"
        icon={<PlusOutlined />}
        onClick={add}
        block
        style={{ marginTop: 4 }}
      >
        添加颜色
      </Button>
    </div>
  );
};

export default ColorArrayEditor;
