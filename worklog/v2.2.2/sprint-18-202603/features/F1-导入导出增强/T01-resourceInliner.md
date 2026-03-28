# T01: resourceInliner — 导出资源内联工具

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

创建工具函数，导出时将大屏配置中的图片 URL 转为 Base64 内联到 JSON 中。

## 技术设计

文件: `pages/screens/utils/resourceInliner.ts`

核心函数:
```typescript
export async function inlineResources(spec: Record<string, unknown>): Promise<{
  spec: Record<string, unknown>;
  inlinedCount: number;
  errors: string[];
}>
```

**逻辑:**
1. 深度遍历 spec 对象，找到所有图片 URL 字段
2. 筛选规则：仅转换 `/analytics/` 或相对路径开头的内部 URL；跳过已有 `data:` 前缀和外部 `https://` URL
3. 对每个 URL 调用 `fetch` → `blob` → `FileReader.readAsDataURL` 转为 Base64
4. 替换原 URL 为 `data:image/xxx;base64,...`
5. 单个图片失败不阻断，记录错误并保留原 URL

**已知图片字段路径:**
- `backgroundImage`（顶层）
- `components[].config.src`
- `components[].config.backgroundImage`
- 通用匹配：config 中值为字符串且匹配 `/\.(png|jpg|jpeg|gif|svg|webp)(\?|$)/i` 的字段

## 影响范围

- 新文件: `pages/screens/utils/resourceInliner.ts`
- 后续被 ScreenHeader 和模板市场导出调用

## 验证
- [ ] 含 backgroundImage 的 spec 导出后，JSON 中该字段变为 data:image/... 格式
- [ ] 含 image 组件的 spec 导出后，config.src 变为 Base64
- [ ] 外部 URL 不被转换
- [ ] 已有 data: 前缀的不重复处理
- [ ] 单张图片 fetch 失败时，其他图片仍正常内联

## 完成标准
- [ ] 函数通过上述验证
- [ ] 返回 inlinedCount 和 errors 供调用者使用
