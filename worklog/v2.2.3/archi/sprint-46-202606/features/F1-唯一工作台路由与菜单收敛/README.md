# F1: 唯一工作台路由与菜单收敛

**优先级**: P0  
**状态**: DONE

## 目标

让 `/workbench` 成为唯一首页，隐藏重复的“数据管理工作台”菜单，并保留旧入口兼容跳转。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 唯一首页路由契约 | P0 | DONE | - |
| T02 | 菜单与角色默认项收敛 | P0 | DONE | T01 |
| T03 | 旧入口兼容跳转与锚点 | P0 | DONE | T01 |

## 完成标准

- [x] `/workbench` 是唯一首页。
- [x] 菜单不再并列展示“数据管理工作台”。
- [x] `/workbench/data-management` 和 `/services/consumption` 兼容跳转。
- [x] source-contract 覆盖菜单 seed、角色默认项、静态路由和动态 resolver。
