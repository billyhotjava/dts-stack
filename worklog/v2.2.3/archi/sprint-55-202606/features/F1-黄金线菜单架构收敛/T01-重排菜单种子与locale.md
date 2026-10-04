# T01: 重排菜单种子与 locale

**优先级**: P0  
**状态**: DONE  
**依赖**: 无

## 目标

以 `dts-admin` 菜单种子为事实源，重排 portal 侧栏一级分区。

## 技术设计

- 修改 `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`。
- 新增“数据基础”“数据消费”一级分区。
- 复用原 `resource` 作为“数据集成”，复用原 `governance` 作为“治理运营”。
- 将原 `studio.metric-modeling` 提升为根分区 `metric-modeling`。
- 补齐 `source/dts-platform-webapp/src/locales/lang/{zh_CN,en_US}/sys.json` 中新 titleKey。

## 影响范围

- dts-admin 菜单种子
- dts-platform-webapp 菜单标题本地化

## 验证

- [x] `jq empty portal-menu-seed.json role-menu-defaults.json sys.json`
- [x] source-contract 覆盖分区顺序和 titleKey。

## 完成标准

- [x] 菜单种子不再把指标建模放在数据开发中心子层。
- [x] 数据消费统一承载服务、BI、大屏。
