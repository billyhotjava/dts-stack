# T02: CoreAssetsBlock 组件

**优先级**: P0
**状态**: READY
**依赖**: F3/T05, F1/T05

## 目标

右 1 栏的核心资产块：按密级降序（S1 > S2 > S3 > S4），同级按更新时间降序，最多 10 行。

## 技术设计

### Props

```ts
import type { LeaderOverviewResponse } from "@/api/services/workbenchService";

export interface CoreAssetsBlockProps {
  role: "EMP" | "DEPT_LEADER" | "INST_LEADER";
  items: LeaderOverviewResponse["topAssets"];
  loading: boolean;
}
```

### 组件

```tsx
// src/pages/workbench/components/CoreAssetsBlock.tsx
import { Card, Empty, List, Skeleton, Tag } from "antd";
import { relativeTime } from "../hooks/relativeTime";

export function CoreAssetsBlock({ role, items, loading }: CoreAssetsBlockProps) {
  const title = role === "EMP"
    ? "我常用的资产"
    : role === "DEPT_LEADER"
      ? "本部门资产 · 按密级"
      : "核心资产 · 按密级";

  return (
    <Card title={title} extra={<a href="/catalog/datasets">查看全部 →</a>}>
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : items.length === 0 ? (
        <Empty description={role === "EMP" ? "还没有常用资产" : "暂无核心资产"} />
      ) : (
        <List
          dataSource={items}
          renderItem={(a) => (
            <List.Item style={{ cursor: "pointer" }} onClick={() => window.open(`/catalog/datasets/${a.id}`, "_blank", "noopener,noreferrer")}>
              <List.Item.Meta
                title={a.name}
                description={
                  <span>
                    {a.updatedAt ? relativeTime(a.updatedAt) : "—"}
                    {a.bizDomain && <> · <Tag color="geekblue" style={{ marginLeft: 4 }}>{a.bizDomain}</Tag></>}
                  </span>
                }
              />
              <Tag color={classificationColor(a.classification)}>{a.classification}</Tag>
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

> `classificationColor` 与 `TopReportsBlock` 重复——抽到 `src/pages/workbench/hooks/classification.ts`：
>
> ```ts
> export function classificationColor(c: string): "red" | "volcano" | "orange" | "blue" | "default" {
>   return { S1: "red", S2: "volcano", S3: "orange", S4: "blue" }[c as "S1"|"S2"|"S3"|"S4"] ?? "default";
> }
> ```

### 点击打开

资产详情路由以项目实际为准（`/catalog/datasets/:id` 或 `/catalog/assets/:id`）。若未确定，临时 `window.open(...)` + TODO 标注。

## 影响范围

- 新增：`src/pages/workbench/components/CoreAssetsBlock.tsx`
- 新增：`src/pages/workbench/hooks/classification.ts`（供 TopReportsBlock 同步使用）
- 修改：`src/pages/workbench/components/TopReportsBlock.tsx`（改用公共 helper）

## 验证

- [ ] 单测：
  - `shows_skeleton_when_loading()`
  - `shows_empty_when_items_empty()`
  - `renders_tag_colors_correctly_for_S1_S4()`
  - `renders_bizDomain_tag_only_when_not_null()`
  - `empty_text_matches_role()`

## 完成标准

- [ ] 密级颜色与 TopReportsBlock 一致（同一 helper）。
- [ ] 排序在前端不再做 sort；后端 F1/T05 保证顺序。
- [ ] 类型严格：`items` 元素字段对齐 `LeaderOverviewResponse.topAssets`。
