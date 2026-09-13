# T03: 数据大屏 chip strip

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标

精简原 `workbench/index.tsx` 的大屏 Table 为一条 chip strip：

- 用户**至少有一个已发布大屏可见时**显示；否则整条隐藏。
- 最多 6 个 chip；超出显示"更多 →"链接跳到大屏列表页。
- 放在 KPI 行之上。

## 技术设计

### 组件

```tsx
// src/pages/workbench/components/ScreenStrip.tsx
import { Space, Tag, Skeleton } from "antd";
import { Monitor } from "lucide-react";
import { useEffect, useState } from "react";
import { analyticsApi } from "@/analytics/api/analyticsApi";

interface PublishedScreen {
  id: number | string;
  name?: string;
  description?: string | null;
  publishedAt?: string | null;
}

export function ScreenStrip() {
  const [screens, setScreens] = useState<PublishedScreen[] | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    analyticsApi.listScreens()
      .then((list) => {
        if (cancelled) return;
        setScreens(Array.isArray(list) ? (list as PublishedScreen[]) : []);
      })
      .catch(() => {
        if (cancelled) return;
        setScreens([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, []);

  if (loading) return <Skeleton.Button active style={{ height: 48, width: "100%" }} />;
  if (!screens || screens.length === 0) return null;

  const shown = screens.slice(0, 6);
  const hasMore = screens.length > 6;

  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", background: "#fafafa", borderRadius: 8 }}>
      <Monitor size={18} color="#4f6ef7" />
      <Space size={[8, 8]} wrap>
        {shown.map((s) => (
          <Tag
            key={s.id}
            color="processing"
            style={{ cursor: "pointer", padding: "4px 12px", fontSize: 13 }}
            onClick={() => window.open(`/analytics/screens/${s.id}`, "_blank", "noopener,noreferrer")}
          >
            {s.name ?? `大屏 ${s.id}`}
          </Tag>
        ))}
      </Space>
      {hasMore && (
        <a href="/analytics/screens" style={{ marginLeft: "auto" }}>更多 →</a>
      )}
    </div>
  );
}
```

### 路径

实际大屏列表页路由与详情页路由以项目实际为准。若不确定，先用 `/analytics/screens` 占位并在注释内 TODO。

## 影响范围

- 新增：`src/pages/workbench/components/ScreenStrip.tsx`

## 验证

- [ ] 单测（mock `analyticsApi.listScreens`）：
  - `shows_skeleton_then_strip_when_list_nonempty()`
  - `hides_when_list_empty()`
  - `hides_when_api_rejects()`
  - `shows_top_6_when_more_than_6_with_more_link()`
  - `no_more_link_when_exactly_6()`

## 完成标准

- [ ] 加载期间用 Skeleton 按钮，不留空白闪烁。
- [ ] 空态彻底隐藏（不留标题行 / 框）。
- [ ] Chip 点击跳转新窗口（`noopener,noreferrer`）。
