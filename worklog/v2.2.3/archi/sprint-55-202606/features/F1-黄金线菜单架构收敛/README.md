# F1: 黄金线菜单架构收敛

**优先级**: P0  
**状态**: DONE

## 目标

将 dts-admin portal 菜单从历史功能分组重排为黄金线信息架构，同时保留既有页面路由和角色菜单绑定。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 重排菜单种子与 locale | P0 | DONE | - |
| T02 | 运行态菜单 reparent 迁移 | P0 | DONE | T01 |
| T03 | 契约测试与运行态验证 | P0 | DONE | T02 |

## 完成标准

- [x] `portal-menu-seed.json` 一级分区顺序符合黄金线。
- [x] 新分区 titleKey 有中英文 locale。
- [x] Liquibase 迁移使用 reparent，不删除菜单可见性绑定。
- [x] 重建 dts-admin 后运行态菜单结构与 seed 一致。
