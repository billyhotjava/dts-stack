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

const colorSlotRowStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '52px minmax(0, 1fr)',
  columnGap: 8,
  alignItems: 'center',
  width: '100%',
  minWidth: 0,
  marginBottom: 6,
};

const colorSlotControlsStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '28px minmax(0, 1fr) 24px',
  columnGap: 6,
  alignItems: 'center',
  minWidth: 0,
};

const LabelRow: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={colorSlotRowStyle}>
    <span style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{label}</span>
    <div style={colorSlotControlsStyle}>{children}</div>
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
      <ColorPicker
        size="small"
        value={value}
        onChange={(_, hex) => onCommit(idx, hex)}
        style={{ width: 28 }}
      />
      <Input
        size="small"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onPressEnter={commit}
        style={{ width: '100%', minWidth: 0 }}
      />
      <Button
        size="small"
        type="text"
        icon={<DeleteOutlined />}
        onClick={() => onRemove(idx)}
        danger
        style={{ width: 24, minWidth: 24, padding: 0 }}
        aria-label={`删除颜色 ${idx + 1}`}
      />
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
    <div style={{ width: '100%', minWidth: 0 }}>
      {value.map((color, idx) => (
        <ColorSlot
          key={`color-${idx}`}
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
        style={{ marginTop: 4, minWidth: 0 }}
      >
        添加颜色
      </Button>
    </div>
  );
};

export default ColorArrayEditor;
