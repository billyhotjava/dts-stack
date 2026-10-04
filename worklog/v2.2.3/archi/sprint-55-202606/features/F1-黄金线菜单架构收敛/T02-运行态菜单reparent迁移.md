# T02: 运行态菜单 reparent 迁移

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

在已有部署中移动原菜单节点，保留 menu id 和角色可见性绑定，避免 seed 变更造成重复菜单。

## 技术设计

- 新增 dts-admin Liquibase changelog：`20260630-02_golden_line_portal_menu_reparent.xml`。
- 创建或更新根分区 `data-foundation`、`consumption`。
- 将主题域、标准、模板移动到 `data-foundation`。
- 将 `metric-modeling` 从 `studio` 下移动到根。
- 将 `services`、`bi-apps`、`screens` 移动到 `consumption`。
- 将原 `governance` 改为“治理运营”，只保留质量/分类运营入口。

## 影响范围

- `portal_menu.parent_id`
- `portal_menu.path`
- `portal_menu.metadata`
- `portal_menu.sort_order`

## 验证

- [x] `xmllint --noout` 通过。
- [x] DO block 在当前 PostgreSQL 中 `BEGIN ... ROLLBACK` 试跑通过。
- [x] source-contract 断言迁移不包含 `DELETE FROM portal_menu_visibility` 或 `DELETE FROM portal_menu`。

## 完成标准

- [x] 运行态迁移保留已有菜单 id 和 visibility 绑定。
