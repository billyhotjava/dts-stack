# T02: 后端菜单 seed 与角色默认入口

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

在 dts-admin 菜单 seed 和角色默认菜单中增加数据资产侧“元数据管理”入口，并通过后端契约测试保证不绑定默认角色。

## 技术设计

- 在 `portal-menu-seed.json` 的 `portal` 子菜单中新增：
  - `key`: `metadata-management`
  - `titleKey`: `sys.nav.portal.dataPortalMetadataManagement`
  - `title`: `元数据管理`
  - `externalLink`: `/catalog/metadata-management`
- 在 `role-menu-defaults.json` 增加同 code/route 的默认项，`requiredRoles` 为空数组。
- 扩展 `PortalMenuSeedDefaultsContractTest`，证明：
  - 数据资产菜单包含新入口。
  - 数据集成菜单仍包含“数据源结构采集”。
  - 两个入口路由不同。

## 影响范围

- `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`
- `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuSeedDefaultsContractTest.java`

## 验证

- [x] `./mvnw -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dspring-boot.build-info.skip=true -Dtest=PortalMenuSeedDefaultsContractTest#dataAssetMetadataManagementSeedKeepsSeparateCollectionAndManagementEntrypoints test`

## 完成标准

- [x] 后端菜单契约测试通过。
- [x] 新入口不会改变已有数据源结构采集入口。
- [x] 新入口不默认绑定任何角色。
