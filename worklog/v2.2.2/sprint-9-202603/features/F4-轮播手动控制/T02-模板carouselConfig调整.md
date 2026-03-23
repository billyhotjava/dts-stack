# T02: 模板 carouselConfig 调整

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
将"科研项目管理指挥大屏"模板的 carouselConfig 改为默认手动模式

## 技术设计

修改 `projectManagementCommandCenterTemplate.ts` 中的 carouselConfig：

```typescript
// Before
carouselConfig: {
    enabled: true,
    intervalSeconds: 15,
    transition: 'fade',
    transitionDuration: 800,
    loop: true,
}

// After
carouselConfig: {
    enabled: true,
    autoPlay: false,        // 默认手动，不自动翻页
    intervalSeconds: 15,    // 播放时的间隔
    transition: 'fade',
    transitionDuration: 800,
    loop: true,
}
```

## 影响范围
- `projectManagementCommandCenterTemplate.ts` — 一行配置改动

## 验证
- [ ] 打开大屏预览 → 默认停在第 1 屏
- [ ] 手动点播放后 → 每 15 秒自动翻页
- [ ] 新建的大屏默认也是手动模式

## 完成标准
- [ ] 既有大屏实例在刷新后生效
