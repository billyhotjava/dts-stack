# T10: 删除旧 ScreenSharePanel 和相关引用

**优先级**: P1
**状态**: READY
**依赖**: T08

## 目标
删除旧的 ScreenSharePanel 组件及其在 ScreenHeader 中的引用

## 技术设计

- 删除 `ScreenSharePanel.tsx`
- ScreenHeader 中将 import 和引用指向新的 `ScreenGrantPanel`
- 移除 analyticsApi 中旧的 `getScreenAcl` / `updateScreenAcl` 方法
- 移除 i18n 中旧分享面板相关的翻译 key

## 影响范围
- 删除: `screens/components/ScreenSharePanel.tsx`
- 修改: `ScreenHeader.tsx` — 引用改为 ScreenGrantPanel
- 修改: `analyticsApi.ts` — 移除旧 ACL API 方法

## 验证
- [ ] 编译无报错
- [ ] 分享按钮打开新面板
- [ ] 无旧 ACL API 调用残留

## 完成标准
- [ ] ScreenSharePanel 文件已删除
- [ ] 无编译错误
