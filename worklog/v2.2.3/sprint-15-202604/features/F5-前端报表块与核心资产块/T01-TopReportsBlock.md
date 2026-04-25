# T01: TopReportsBlock 组件

**优先级**: P0
**状态**: READY
**依赖**: F3/T05, F1/T04

## 目标

左 2 栏的 TOP 报表块：按访问量降序，最多 10 行；点击打开报表 URL 并调用 `reportsService.visit` 埋点。

## 技术设计

### Props

```ts
import type { LeaderOverviewResponse } from "@/api/services/workbenchService";

export interface TopReportsBlockProps {
  role: "EMP" | "DEPT_LEADER" | "INST_LEADER";
  items: LeaderOverviewResponse["topReports"];
  loading: boolean;
  onEmpty?: () => React.ReactNode; // 自定义空态
}
```

### 组件

```tsx
// src/pages/workbench/components/TopReportsBlock.tsx
import { Card, Empty, List, Skeleton, Tag } from "antd";
import { useNavigate } from "react-router-dom";
import reportsService from "@/api/services/reportsService";
import { relativeTime } from "../hooks/relativeTime"; // 抽成公共 helper（T03/T04 复用）

export function TopReportsBlock({ role, items, loading, onEmpty }: TopReportsBlockProps) {
  const title = role === "EMP" ? "我常用的报表" : role === "DEPT_LEADER" ? "本部门 TOP 报表" : "全所 TOP 报表";
  const emptyText = role === "EMP"
    ? <div>还没有访问过任何报表，<a href="/reports">去报表中心看看</a></div>
    : <div>暂无已发布报表</div>;

  return (
    <Card title={title} extra={<a href="/reports">查看全部 →</a>}>
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : items.length === 0 ? (
        (onEmpty?.() ?? <Empty description={emptyText} />)
      ) : (
        <List
          dataSource={items}
          renderItem={(r) => (
            <List.Item
              style={{ cursor: "pointer" }}
              onClick={async () => {
                await reportsService.visit({ id: r.id, title: r.title, classification: r.classification });
                // 打开策略沿用项目既有 resolveRouteForOpen 逻辑；此处直接 window.open 占位
                window.open(`/reports/${r.id}`, "_blank", "noopener,noreferrer");
              }}
            >
              <List.Item.Meta
                title={r.title}
                description={
                  <span>
                    访问 {r.visits.toLocaleString()} · {r.lastVisitedAt ? relativeTime(r.lastVisitedAt) : "—"}
                  </span>
                }
              />
              <div>
                {r.bizDomain && <Tag color="geekblue">{r.bizDomain}</Tag>}
                <Tag color={classificationColor(r.classification)}>{r.classification}</Tag>
              </div>
            </List.Item>
          )}
        />
      )}
    </Card>
  );
}

function classificationColor(c: string): string {
  return { S1: "red", S2: "volcano", S3: "orange", S4: "blue" }[c as "S1" | "S2" | "S3" | "S4"] ?? "default";
}
```

### relativeTime helper

抽自当前 `workbench/index.tsx:44` 已有逻辑，移到 `src/pages/workbench/hooks/relativeTime.ts`：

```ts
export function relativeTime(value?: string | null): string {
  if (!value) return "";
  const diff = Date.now() - new Date(value).getTime();
  if (diff < 0 || Number.isNaN(diff)) return value;
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "刚刚";
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  return `${days} 天前`;
}
```

### 点击打开策略

查 `src/analytics/helpers/resolveAnalyticsUrl.ts`（旧 `index.tsx:12` 有 import `resolveRouteForOpen`），若适合沿用则复用；否则先用 `window.open`，在注释里 TODO 指向统一的打开策略。

## 影响范围

- 新增：`src/pages/workbench/components/TopReportsBlock.tsx`
- 新增：`src/pages/workbench/hooks/relativeTime.ts`

## 验证

- [ ] 单测：
  - `shows_skeleton_when_loading()`
  - `shows_empty_for_EMP_with_link()`
  - `shows_empty_for_DEPT_LEADER()`
  - `renders_up_to_10_items()`
  - `calls_reportsService_visit_on_row_click()`
  - `renders_classification_tag_with_correct_color()`
  - `renders_bizDomain_tag_only_when_not_null()`

## 完成标准

- [ ] 标题随 role 变化（员工 / 部门 / 全所）。
- [ ] 空态对员工有引导链接；对领导只是中性"暂无"。
- [ ] 点击行后 `reports/visit` 埋点调用成功（哪怕失败也不阻塞 open）。
