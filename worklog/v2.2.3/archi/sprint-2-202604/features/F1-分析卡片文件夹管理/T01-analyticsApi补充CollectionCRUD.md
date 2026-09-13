# T01: analyticsApi 补充 Collection CRUD

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
在 `analyticsApi.ts` 中补充 Collection 的创建、更新、删除 API 封装。

## 技术设计

在 `analyticsApi` 对象中新增以下方法，调用 Metabase 标准 REST 接口：

```typescript
createCollection: (body: { name: string; parent_id?: number | null; description?: string | null }) =>
    sendJson<CollectionListItem>("/bi/api/collection", body),

updateCollection: (id: number, body: { name?: string; parent_id?: number | null; description?: string | null }) =>
    requestJson<CollectionListItem>(`/bi/api/collection/${encodeURIComponent(String(id))}`, "PUT", body),

deleteCollection: (id: number) =>
    requestJson<void>(`/bi/api/collection/${encodeURIComponent(String(id))}`, "DELETE"),
```

同时确认 `CollectionListItem` 类型已有 `parent_id` 字段，如没有需补充：

```typescript
export type CollectionListItem = {
    id: number | "root";
    name?: string;
    description?: string | null;
    parent_id?: number | null;  // ← 确认存在
    archived?: boolean;
    can_write?: boolean;
};
```

## 影响范围
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts`

## 验证
- [ ] `createCollection` 调用成功，返回新 collection 对象
- [ ] `updateCollection` 可重命名和修改 parent_id
- [ ] `deleteCollection` 调用成功
- [ ] TypeScript 编译无错误

## 完成标准
- [ ] 三个方法已添加到 analyticsApi
- [ ] CollectionListItem 类型包含 parent_id
