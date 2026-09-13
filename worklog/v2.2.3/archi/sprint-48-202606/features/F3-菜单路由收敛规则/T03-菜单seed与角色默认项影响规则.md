# T03: 菜单 seed 与角色默认项影响规则

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

防止菜单整改破坏角色绑定、默认入口和客户已有链接。

## 技术设计

菜单整改必须同时检查：

- `portal-menu-seed.json`
- `role-menu-defaults.json`
- zh/en locale
- static route
- dynamic resolver override
- source-contract test
- role/menu visibility 是否需要迁移

## 影响范围

- `assets/dts-frontend-refactor-rules.md`

## 验证

- [x] 规则明确写入 sprint 资产

## 完成标准

- [x] 后续菜单变更具备可审查 checklist
