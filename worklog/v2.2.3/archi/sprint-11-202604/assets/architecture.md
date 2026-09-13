# SQL IDE 架构

## 组件树

```
SqlIde
├─ ModeSwitcher (F6 右上角)
├─ ActivityBar (F3/T12, 44px icon 栏)
├─ SidePanel (F3/T12, 可拖拽)
│  └─ { Schema | History | Saved | Search | Copilot }
├─ 中央列
│  ├─ TabBar (F2/T10)
│  ├─ SqlEditor (F1: Monaco + completion + shortcuts + formatter)
│  └─ BottomPanel
│     ├─ Header: status + cancel + ExportMenu + SubQueryButton
│     └─ BottomTabs (F5/T25)
│        └─ { Results | Chart | Pivot | Query Plan | Log }
└─ SaveQueryDialog (F3/T15, Ctrl+S 弹)
```

## Store

- `useTabStore` (F2) — Tab 状态 + 持久化
- `useLayoutStore` (F3) — ActivityBar + SidePanel 尺寸
- `useUiModeStore` (F6) — 简洁/高级模式

## 后端

- `/api/sql/v2/*` 全新
- `/api/sql/*` legacy 保留
- 审计 → `SqlIdeAuditActions` + `auditService.record()`
- 结果分块 → `query_execution_chunk` JSONB
- 二次查询 → PG UNLOGGED + ownership check + read-only

## Feature Flag

`WEBAPP_ENABLE_SQL_IDE_V2` (webapp) — 默认 false；前端路由条件分派。
