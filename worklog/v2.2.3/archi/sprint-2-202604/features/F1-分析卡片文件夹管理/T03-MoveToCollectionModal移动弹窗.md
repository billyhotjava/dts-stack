# T03: MoveToCollectionModal 移动弹窗

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
新建 `MoveToCollectionModal.tsx` 组件，提供文件夹选择弹窗，用于将卡片移动到指定文件夹。

## 技术设计

### 文件位置
`source/dts-platform-webapp/src/analytics/components/MoveToCollectionModal.tsx`

### Props 接口
```typescript
type MoveToCollectionModalProps = {
    open: boolean;
    cardIds: number[];                        // 待移动的卡片 ID 列表
    onClose: () => void;
    onSuccess: () => void;                    // 移动成功后刷新回调
};
```

### UI 结构
```
┌─ 移动到文件夹 ──────────────────────┐
│                                      │
│  📋 未分类                           │
│  📁 项目管理                         │
│    📁 周报                           │
│    📁 月报                           │
│  📁 技术分析                         │
│                                      │
│              [取消]  [确定]           │
└──────────────────────────────────────┘
```

### 实现细节
- 打开时调用 `listCollections()` 获取所有 collection
- 构建嵌套树结构，顶部增加「未分类」节点（`collection_id = null`）
- 使用 antd `<Tree>` 单选模式（`selectable`，不 `checkable`）
- 确定时遍历 `cardIds`，逐个调用 `analyticsApi.updateCard(id, { collection_id })` 
- 显示 loading 状态，完成后 `message.success` 并触发 `onSuccess`

### 样式
- antd `<Modal>` 标准样式，宽度 400px
- Tree 节点带文件夹图标（antd `<FolderOutlined />`）

## 影响范围
- 新建 `source/dts-platform-webapp/src/analytics/components/MoveToCollectionModal.tsx`

## 验证
- [ ] 弹窗正确展示文件夹树（含嵌套和「未分类」）
- [ ] 选中文件夹后点击确定，卡片 collection_id 更新成功
- [ ] 批量移动（多个 cardIds）正常工作
- [ ] 移动成功后 onSuccess 回调触发
- [ ] Chrome 95 兼容

## 完成标准
- [ ] 组件功能完整可用
- [ ] 支持单个和批量移动
