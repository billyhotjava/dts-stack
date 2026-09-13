# T03: DomainMatrix 色块组件

**优先级**: P0
**状态**: READY
**依赖**: F3/T05, F1/T06

## 目标

实现 `DomainMatrix` 组件：仅所领导 + 业务域可用 + 数据非空时渲染，1 行 6 列色块布局（超出聚合为"其他"）。颜色按访问量深浅渐变（同色系），点击触发 `onSelect(domain)`。

## 技术设计

### Props

```ts
export interface DomainCell {
  domain: string;        // 域 code 或 "__OTHER__" / "__UNCATEGORIZED__"
  domainName: string;
  visits: number;
}

export interface DomainMatrixProps {
  visible: boolean;                 // 由父层控制（role + bizDomainAvailable + data.length > 0）
  cells: DomainCell[];              // 后端已按访问量降序并补"其他"桶
  activeDomain: string | null;      // 当前全局选中的 bizDomain，用于高亮
  onSelect: (domain: string | null) => void; // 点击切换；再次点击当前格 → 传 null 清空
}
```

### 颜色映射

同色系渐变（项目主色 `#4f6ef7`）：

```ts
const BASE_COLOR = { r: 79, g: 110, b: 247 };

function shadeColor(weight: number): string {
  // weight ∈ [0, 1]：0 最浅，1 最深
  const mix = (base: number, target: number) => Math.round(base + (target - base) * weight);
  const r = mix(235, BASE_COLOR.r);
  const g = mix(240, BASE_COLOR.g);
  const b = mix(255, BASE_COLOR.b);
  return `rgb(${r}, ${g}, ${b})`;
}

function weightOf(visits: number, min: number, max: number): number {
  if (max <= min) return 0.5;
  return Math.max(0.1, Math.min(1, (visits - min) / (max - min)));
}

const OTHER_COLOR = "#bfbfbf"; // 灰色：单独标识"其他"
```

### 组件实现

```tsx
// src/pages/workbench/components/DomainMatrix.tsx
import { useMemo } from "react";
import { Card, Tooltip } from "antd";
import { auditLog } from "@/utils/audit";

// ...import/types 同上

export function DomainMatrix({ visible, cells, activeDomain, onSelect }: DomainMatrixProps) {
  const stats = useMemo(() => {
    if (cells.length === 0) return { min: 0, max: 0 };
    const visits = cells.filter((c) => c.domain !== "__OTHER__").map((c) => c.visits);
    return { min: Math.min(...visits), max: Math.max(...visits) };
  }, [cells]);

  if (!visible || cells.length === 0) return null;

  const handleClick = (cell: DomainCell) => {
    if (cell.domain === "__OTHER__" || cell.domain === "__UNCATEGORIZED__") {
      // "其他" / "未分类" 桶点击不切换（没有对应 bizDomain 值可设）
      return;
    }
    auditLog("WORKBENCH_DOMAIN_DRILL", { domain: cell.domain });
    onSelect(cell.domain === activeDomain ? null : cell.domain);
  };

  return (
    <Card size="small" styles={{ body: { padding: 12 } }}>
      <div style={{ display: "grid", gridTemplateColumns: `repeat(${cells.length}, 1fr)`, gap: 8 }}>
        {cells.map((c) => {
          const isOther = c.domain === "__OTHER__";
          const isUncat = c.domain === "__UNCATEGORIZED__";
          const isActive = c.domain === activeDomain;
          const bg = isOther ? OTHER_COLOR : shadeColor(weightOf(c.visits, stats.min, stats.max));
          const cursor = isOther || isUncat ? "default" : "pointer";
          return (
            <Tooltip key={c.domain} title={`${c.domainName} · 访问 ${c.visits}`}>
              <div
                onClick={() => handleClick(c)}
                style={{
                  background: bg,
                  padding: 16,
                  borderRadius: 8,
                  color: "#fff",
                  cursor,
                  textAlign: "center",
                  border: isActive ? "2px solid #faad14" : "2px solid transparent",
                  transition: "all 0.2s",
                  minHeight: 72,
                }}
                role={isOther || isUncat ? undefined : "button"}
                aria-pressed={isActive}
              >
                <div style={{ fontSize: 14, fontWeight: 500, marginBottom: 4 }}>{c.domainName}</div>
                <div style={{ fontSize: 18, fontWeight: 600 }}>{c.visits.toLocaleString()}</div>
              </div>
            </Tooltip>
          );
        })}
      </div>
    </Card>
  );
}
```

### 无障碍

- 普通域格：`role="button"`, `aria-pressed={isActive}`, `cursor: pointer`.
- "其他" / "未分类"：不可点击，不带 button 角色。

## 影响范围

- 新增：`src/pages/workbench/components/DomainMatrix.tsx`

## 验证

- [ ] 单测：
  - `not_rendered_when_visible_false()`
  - `not_rendered_when_cells_empty()`
  - `renders_all_cells_in_grid()`
  - `highlights_active_domain()`
  - `emits_onSelect_with_domain_when_cell_clicked()`
  - `emits_onSelect_null_when_active_cell_clicked_again()`
  - `other_bucket_click_is_noop()`
  - `uncategorized_bucket_click_is_noop()`
  - `audit_event_emitted_on_valid_click()`

## 完成标准

- [ ] 色块在 1440/1024/768 宽度下不换行（1 行 ≤ 7 格）；768 以下允许换行。
- [ ] 键盘 Tab 可聚焦正常格，Enter/Space 等价点击（`onKeyDown` 处理）→ 若时间紧可先以 role="button" 满足基础可用性，深度 a11y 标为后续。
- [ ] 切换全局 `bizDomain` 后 `activeDomain` 高亮同步更新。
