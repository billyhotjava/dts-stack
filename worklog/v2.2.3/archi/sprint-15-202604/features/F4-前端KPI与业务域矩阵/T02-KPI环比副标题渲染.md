# T02: KPI 环比 / 比例副标题渲染

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

把 T01 中 `secondary` 副标题的样式做完整：

- 环比 `visitsMoM > 0` → 绿色 + `↑`
- 环比 `visitsMoM < 0` → 红色 + `↓`
- 环比为 0 → 灰色，无箭头
- `null` / `undefined` → 不渲染副标题（防止"环比 -"这种丑行）

## 技术设计

抽出独立组件 `KpiSecondary`，方便测试与复用：

```tsx
// src/pages/workbench/components/KpiSecondary.tsx
import { ArrowDownOutlined, ArrowUpOutlined } from "@ant-design/icons";

export interface MoMSecondaryProps {
  mom: number | null | undefined;
  prefix?: string;
}

export function MoMSecondary({ mom, prefix = "环比" }: MoMSecondaryProps) {
  if (mom === null || mom === undefined) return null;
  const pct = Math.abs(mom * 100).toFixed(1);
  if (mom === 0) {
    return <span style={{ color: "#8c8c8c", fontSize: 12 }}>{prefix} 持平</span>;
  }
  const positive = mom > 0;
  return (
    <span style={{ color: positive ? "#52c41a" : "#ff4d4f", fontSize: 12 }}>
      {positive ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {prefix} {pct}%
    </span>
  );
}

export interface RatioSecondaryProps {
  ratio: number | null | undefined;
  prefix?: string;
}

export function RatioSecondary({ ratio, prefix = "占比" }: RatioSecondaryProps) {
  if (ratio === null || ratio === undefined) return null;
  return <span style={{ color: "#8c8c8c", fontSize: 12 }}>{prefix} {(ratio * 100).toFixed(1)}%</span>;
}

export interface StaticSecondaryProps {
  text: string | null | undefined;
}

export function StaticSecondary({ text }: StaticSecondaryProps) {
  if (!text) return null;
  return <span style={{ color: "#8c8c8c", fontSize: 12 }}>{text}</span>;
}
```

在 `KpiRow.tsx` 里用这些组件替换 T01 版本的字符串 `secondary`：

```tsx
type CardDef =
  | { key: string; title: string; value: number; kind: "static"; text?: string }
  | { key: string; title: string; value: number; kind: "mom"; mom: number | null }
  | { key: string; title: string; value: number; kind: "ratio"; ratio: number | null }
  | { key: string; title: string; value: number; kind: "none" };

// buildCards 返回 CardDef[]
// 渲染时 switch card.kind 选择对应 <MoMSecondary /> / <RatioSecondary /> / <StaticSecondary />
```

## 影响范围

- 新增：`src/pages/workbench/components/KpiSecondary.tsx`
- 修改：`src/pages/workbench/components/KpiRow.tsx`（替换 T01 版本的字符串 secondary）

## 验证

- [ ] 单测：
  - `MoMSecondary_renders_null()`
  - `MoMSecondary_renders_positive_green_up()`
  - `MoMSecondary_renders_negative_red_down()`
  - `MoMSecondary_renders_zero_gray_持平()`
  - `RatioSecondary_renders_null_nothing()`
  - `RatioSecondary_renders_percent()`
- [ ] 快照：KpiRow 三角色快照更新为带 icon 版本。

## 完成标准

- [ ] null / 0 / 正 / 负 四种情形均有明确不同视觉输出。
- [ ] 颜色使用 AntD 语义色（`#52c41a` / `#ff4d4f` / `#8c8c8c`），与项目其他 KPI 块一致。
- [ ] 不在组件里拼中英文混合奇怪文案（"环比 8.0%" ✅；"环比 8%增长" ❌）。
