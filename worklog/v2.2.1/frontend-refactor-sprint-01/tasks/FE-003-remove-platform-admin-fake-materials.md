# FE-003

## 标题

删除 `platform/admin` 的假素材、demo 服务与占位入口。

## 范围

- `source/dts-platform-webapp/src/layouts/components/notice.tsx`
- `source/dts-admin-webapp/src/layouts/components/notice.tsx`
- 两端 `demoService.ts`
- `FeaturePlaceholder.tsx`
- `V3Placeholder.tsx`
- `sys/others` 下仅用于演示的页面和素材

## 目标

- 生产路径不再出现 fake notice、demo service、placeholder 页
- 路由和菜单删除后不留悬空导入

## 交付

- 直接删除纯 demo/dev 代码
- 对真实页面仍依赖的部分，用真实空态或禁用态替换

## 验收

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- 代码搜索中不再有 customer-facing 的 `demo/placeholder/faker` 残留

## 风险

- `notice.tsx` 不是独立页面，误删会影响全局壳层
- `sys/others` 下部分内容可能被路由树间接引用，删除要先断引用
