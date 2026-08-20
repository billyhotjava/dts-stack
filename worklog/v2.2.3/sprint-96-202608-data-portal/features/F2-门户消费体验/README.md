# F2：门户消费体验

**优先级**：P0  
**状态**：DRAFT（等待 F1/T01）

## 目标

业务用户在一个客户可识别的“数据门户”页面完成主题域浏览、搜索、选择大屏、稳定深链和发布态查看，并可进入大屏管理。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Route | `/bi/portal`、`/bi/portal/:screenId` | 无 screenId 选首个；非法/不可见 ID 显示明确状态 |
| Domain | `GET /api/catalog/domains/tree` | 失败时平铺，不阻断可读大屏 |
| Runtime | `/bi/screens/{id}/preview?mode=published&embed=1&scaleMode=fit` | `fallbackDraft=false`；同源 iframe |
| Menu | `sys.nav.portal.biScreens` | title=数据门户，externalLink=/bi/portal，ID/visibility 保持 |

## UI/UX 规格

```text
┌ 数据门户 ─────────────── [大屏管理] [刷新] ┐
│ ┌ 左侧 260px ─────┐ ┌ 当前发布大屏 ──────┐ │
│ │ 搜索大屏          │ │                     │ │
│ │ 主题域 A          │ │ published runtime   │ │
│ │   ├ 大屏甲        │ │                     │ │
│ │   └ 大屏乙        │ │                     │ │
│ │ 未归类            │ │                     │ │
│ └─────────────────┘ └─────────────────────┘ │
└─────────────────────────────────────────────┘
```

- 空态：没有可访问的已发布大屏，解释“需发布/授权”，提供大屏管理入口。
- 加载态：左树骨架与右侧准备提示，不跳动按钮宽度。
- 错误态：目录错误可重试；主题域错误退化为平铺；运行态错误在右侧呈现。
- 成功态：选中叶子高亮；URL 同步；刷新保留 screenId；标题显示版本/密级。
- 兼容：Chrome95，不用 `:has`、container query、新 viewport units、toSorted/Object.groupBy。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 门户树、搜索、深链与四态 | DRAFT | F1/T01 |
| T02 | published/embed 运行态与菜单原位收敛 | DRAFT | T01 |

## Definition of Ready

- [x] UI 路由、控件、四态与走查已命名。
- [x] API/数据/迁移链无 TBD。
- [ ] F1/T01 已 GREEN。
