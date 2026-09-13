# Chrome 95 兼容静态检查清单

以下清单不依赖真 Chrome 95，在任何开发机上即可跑。通过 = 至少 API/语法层面 Chrome 95 能解析执行。

## 已验证 API（Chrome 95 原生支持）

- ResizeObserver
- `crypto.randomUUID()`
- CSS `clamp()` / `min()` / `max()`
- CSS `object-fit` / `object-position`
- `requestAnimationFrame` / `cancelAnimationFrame`
- `CSS.escape`
- `linear-gradient`

## 已禁用 API（Chrome 95 不支持 — 在代码审查中已 grep 确认未出现）

- `@container` queries / `cqw` / `cqh` / `cqi` 等 container query units
- `:has()` selector
- `structuredClone()`
- `<dialog>` 元素
- `element.showPopover()` / `popover` 属性
- `Array.prototype.findLast`（ES2023）

## 自动化 grep（在仓库根执行）

```bash
# 以下应返回 0 匹配（若有匹配说明用到了 Chrome 95 不支持的 API）
cd source/dts-platform-webapp/src/analytics/pages/screens

grep -r --include="*.ts" --include="*.tsx" --include="*.css" \
  -E '@container|:has\(|cqw|cqh|structuredClone\(|<dialog|findLast\(' .
```

## 浏览器版本矩阵参考

| API | Chrome 95 | Chrome 105 | Chrome 109 | Firefox 102 |
|-----|-----------|------------|------------|-------------|
| ResizeObserver | ✅ | ✅ | ✅ | ✅ |
| CSS clamp() | ✅ | ✅ | ✅ | ✅ |
| object-fit | ✅ | ✅ | ✅ | ✅ |
| cqw (container queries) | ❌ | ✅ | ✅ | ✅ |
| :has() | ❌ | ❌ | ✅ | ✅ |
| structuredClone | ❌ | ✅ | ✅ | ✅ |

## 静态检查作为前置条件

IT README 中的 TC-01~TC-05 是 **运行时** 行为验证，必须在真实 Chrome 95 上跑。
本文件提供的是代码层面保证，能避免显而易见的 API 不兼容导致的白屏/崩溃。

## 依赖库 Chrome 95 兼容性记录

| 库 | 版本 | 备注 |
|---|---|---|
| react | ^18.x | ✅ |
| react-grid-layout | ^1.5.3 | ✅（F1/T01 验证） |
| echarts | ^5.x | ✅ |
| echarts-for-react | ^3.x | ✅ |
| antd | ^5.x | ✅（部分次要 CSS 效果依赖 :where 但不影响功能） |
