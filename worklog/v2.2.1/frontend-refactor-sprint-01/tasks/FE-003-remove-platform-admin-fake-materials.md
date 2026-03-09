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

## 当前进度

- 已删除两端 `demoService.ts`
- 已删除两端 fake `notice.tsx`
- 已删除 `FeaturePlaceholder.tsx` 与 `V3Placeholder.tsx`
- 已移除 `register-koal-devtools.ts` 及其在 `main.tsx` 的 dev-only 注册入口
- `TaskSchedulingPage.tsx` 已从纯占位页改成真实任务运营入口页
- 已删除 `admin` 侧 `sys/others/calendar`、`sys/others/kanban`、`sys/others/permission` 的 demo 页面与素材
- 已删除 `platform` 侧残留的 `sys/others/calendar`、`sys/others/kanban` 假素材工具文件
- `platform/admin` 两端构建已通过
- 代码搜索中不再有 customer-facing 的 `demo/placeholder/faker` 残留

## 验收

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- 代码搜索中不再有 customer-facing 的 `demo/placeholder/faker` 残留

## 风险

- `notice.tsx` 不是独立页面，误删会影响全局壳层
- `sys/others` 下部分内容可能被路由树间接引用，删除要先断引用

## 本轮验证

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `rg -n "faker|FeaturePlaceholder|V3Placeholder|demoService|register-koal-devtools|page-test|sys/others/(calendar|kanban|permission)" source/dts-platform-webapp/src source/dts-admin-webapp/src`
