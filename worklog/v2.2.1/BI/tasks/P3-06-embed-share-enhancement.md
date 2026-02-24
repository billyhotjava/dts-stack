# P3-06 嵌入分享增强

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 2 - 体验优化`
`inspiration`: `DataEase(iframe/div/模块嵌入 + 密码保护 + 自定义 URL) + 现场交付集成需求`

## 目标

从当前单一的 UUID 公开链接扩展为 4 种嵌入方式，支持密码保护和外部参数透传，满足门户集成和安全分享需求。

## 差距分析

| 嵌入方式 | DTS 当前 | DataEase | 目标 |
|---------|:---:|:---:|:---:|
| 公开链接 (UUID) | ✅ | ✅ | 保持 |
| iframe 嵌入代码 | ❌ | ✅ | 新增 |
| 密码保护 | P0-04 已有口令 | ✅ | 增强 UX |
| URL 参数透传 | ❌ | ✅ | 新增 |
| 自定义 URL 后缀 | ❌ | ✅ | 新增 |
| 嵌入时隐藏控件 | ❌ | ✅ | 新增 |

## 子任务

### 1. iframe 嵌入代码生成

**文件**: `ScreenHeader.tsx` (分享面板)

新增"嵌入代码"Tab，自动生成：
```html
<iframe
  src="https://domain/analytics/public/screen/{uuid}?embed=1&hideControls=1"
  width="100%" height="600"
  frameborder="0" allowfullscreen
  style="border: none;"
></iframe>
```

提供：
- 宽高自定义输入
- "隐藏控件栏" 开关 → 添加 `hideControls=1` 参数
- "自适应高度" 开关 → 添加 `autoHeight=1` 参数
- 一键复制按钮

### 2. URL 参数透传到全局变量

**文件**: `PublicScreenPage.tsx`

- 读取 URL search params，匹配 `globalVariables` 的 key。
- 规则：`?var_{key}=value` 映射到全局变量 `key`。
- 安全校验：只允许已声明的 globalVariable key，忽略未声明的参数。
- 示例：`/public/screen/abc?var_region=北京&var_year=2025`

**实现**:
```typescript
const params = new URLSearchParams(location.search);
for (const [k, v] of params) {
  if (k.startsWith('var_')) {
    const varKey = k.slice(4);
    if (definitions.some(d => d.key === varKey)) {
      runtime.setVariable(varKey, v, 'url-param');
    }
  }
}
```

### 3. 嵌入模式 UI 简化

**文件**: `PublicScreenPage.tsx`

当 URL 包含 `embed=1` 时：
- 隐藏顶部控制栏（缩放/设备切换）
- 隐藏页面标题
- 隐藏背景边距
- 自动适配父容器尺寸

当 URL 包含 `hideControls=1` 时：
- 仅隐藏缩放控件

### 4. 自定义 URL 后缀

**后端**: 分享配置新增 `customSlug` 字段。

- 路由：`/analytics/public/screen/:slugOrUuid`
- 后端先按 slug 查找，未命中则按 UUID 查找。
- slug 格式校验：`^[a-z0-9][a-z0-9-]{2,48}$`
- slug 唯一性校验。

### 5. 分享面板 UX 增强

**文件**: `ScreenHeader.tsx` (分享面板)

整合为 3 个 Tab：
- **链接分享** — UUID 链接 + 自定义后缀 + 密码 + 过期时间
- **嵌入代码** — iframe 代码 + 配置项
- **参数说明** — 可透传的全局变量列表 + URL 参数格式说明

## Chrome 95 兼容性

- `URLSearchParams` Chrome 49+ ✅。
- `navigator.clipboard` 已有 fallback ✅。

## 验收标准

- iframe 嵌入代码可在第三方页面正常加载大屏。
- URL 参数正确映射到全局变量并触发组件更新。
- `embed=1` 模式下无多余控件。
- 自定义 slug 可访问且唯一。
- Chrome 95 下分享面板正常交互。

## 风险与回滚

- 风险：URL 参数注入安全问题。
- 回滚：仅允许写入已声明的全局变量，值进行 sanitize 处理。
