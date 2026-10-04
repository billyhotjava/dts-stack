# F1: 菜单角色路由收敛

**优先级**: P0  
**状态**: DONE

## 目标

把指标入口收敛为平台内一组 canonical 路由，同时保留旧链接兼容，不再把客户带进旧 dts-metrics iframe。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 门户菜单 seed 对齐 | P0 | DONE | F0/T01 |
| T02 | 角色默认项与 locale 对齐 | P0 | DONE | T01 |
| T03 | 旧指标路由兼容重定向 | P0 | DONE | T02 |
| T04 | 菜单路由 source-contract | P0 | DONE | T03 |

## 完成标准

- [x] `portal-menu-seed.json`、`role-menu-defaults.json`、locale、静态路由、动态 resolver 指向一致。
- [x] 旧 `/bi-apps/metrics/*` 和 `/modeling/semantic-center/*` 不再 iframe 旧服务。
- [x] query/hash 在兼容跳转中保留。
