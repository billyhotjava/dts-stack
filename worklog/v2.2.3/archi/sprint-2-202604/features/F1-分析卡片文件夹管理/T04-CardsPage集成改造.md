# T04: CardsPage 集成改造

**优先级**: P0
**状态**: READY
**依赖**: T02, T03

## 目标
改造现有 `CardsPage.tsx`，集成左侧 CollectionTree 和 MoveToCollectionModal，实现左右分栏布局。

## 技术设计

### 布局改造
将 CardsPage 从纯列表改为左右分栏，样式对齐 DbtFileBrowserPage：

```tsx
<div className="space-y-4">
    <PageHeader title={...} actions={...} />
    <Card styles={{ body: { padding: 0 } }}>
        <div className="flex min-h-[720px]">
            {/* 左侧文件夹树 */}
            <CollectionTree
                selectedKey={selectedCollection}
                onSelect={setSelectedCollection}
                onCollectionsChange={handleCollectionsChange}
            />
            {/* 右侧卡片列表 */}
            <div className="flex min-w-0 flex-1 flex-col p-4">
                {/* 现有搜索、批量操作、Table 代码 */}
            </div>
        </div>
    </Card>
</div>
```

### 状态管理
新增状态：
- `selectedCollection: string` — 默认 `"__all__"`
- `moveModalOpen: boolean` — 移动弹窗开关
- `moveCardIds: number[]` — 待移动的卡片 ID

### 卡片过滤逻辑
根据 `selectedCollection` 过滤 `filteredCards`：
- `__all__`：显示所有卡片（现有行为）
- `__uncategorized__`：过滤 `collection_id === null || collection_id === undefined`
- 数字 ID：过滤 `collection_id === Number(selectedCollection)`，**含子文件夹**（递归收集所有后代 collection ID）

### 表格列改造
操作列新增「移动」按钮：
```tsx
<Button type="link" size="small" icon={<FolderOutlined />}
    onClick={() => { setMoveCardIds([record.id]); setMoveModalOpen(true); }}>
    移动
</Button>
```

### 批量操作栏改造
已选卡片时，新增「移动到...」按钮（在现有「批量删除」旁边）：
```tsx
<Button size="small" icon={<FolderOutlined />}
    onClick={() => { setMoveCardIds(selectedRowKeys.map(Number)); setMoveModalOpen(true); }}>
    移动到...
</Button>
```

### 刷新联动
- `handleCollectionsChange`：文件夹增删改后重新加载卡片列表（卡片 collection_id 可能变化）
- MoveToCollectionModal `onSuccess`：重新加载卡片列表 + 关闭弹窗 + 清空选中

### 保留的现有功能
- 搜索过滤
- 批量导入 SQL（BatchImportCardsModal）
- 批量删除
- 单个删除
- 分页

## 影响范围
- 改造 `source/dts-platform-webapp/src/analytics/pages/CardsPage.tsx`

## 验证
- [ ] 页面显示左右分栏布局
- [ ] 点击文件夹节点正确过滤右侧卡片
- [ ] 「全部卡片」显示所有，「未分类」显示无归属卡片
- [ ] 选择文件夹时含子文件夹的卡片也显示
- [ ] 操作列「移动」按钮可用
- [ ] 批量「移动到...」按钮可用
- [ ] 移动后列表刷新正确
- [ ] 现有功能（搜索、批量导入、批量删除）不受影响
- [ ] 样式与 DbtFileBrowserPage 一致
- [ ] Chrome 95 兼容

## 完成标准
- [ ] 左右分栏布局正常
- [ ] 文件夹过滤功能正确
- [ ] 移动功能可用
- [ ] 所有现有功能保持正常
