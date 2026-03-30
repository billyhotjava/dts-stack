# T04: dts-admin统一管理BI菜单与默认入口

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

将 BI 菜单定义、层级、排序、显隐、权限与默认入口配置统一收口到 `dts-admin`。

## 技术设计

- 在 `dts-admin` 中新增 BI 菜单种子与菜单管理配置；
- 菜单 path 指向 canonical route；
- 默认首页由平台配置和菜单体系控制，不再由前端硬编码。

## 影响范围

- `source/dts-admin/**`
- `source/dts-platform/**`
- `source/dts-platform-webapp/src/api/services/menuService.ts`
- 菜单种子与权限配置

## 验证

- [ ] BI 菜单可在 `dts-admin` 中查看与维护
- [ ] 平台登录后按配置进入正确 BI 首页
- [ ] 菜单权限变更能实时影响前端导航

## 完成标准

- [ ] `dts-admin` 成为 BI 菜单唯一真源
- [ ] 平台前端不再硬编码 BI 一级菜单
