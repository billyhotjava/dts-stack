# T02: resourceRestorer — 导入资源还原工具

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

创建工具函数，导入时将 JSON 中内联的 Base64 图片上传到服务端并替换为 URL。

## 技术设计

文件: `pages/screens/utils/resourceRestorer.ts`

核心函数:
```typescript
export async function restoreResources(spec: Record<string, unknown>): Promise<{
  spec: Record<string, unknown>;
  restoredCount: number;
  errors: string[];
}>
```

**逻辑:**
1. 深度遍历 spec 对象，找到所有 `data:image/...;base64,...` 格式的值
2. 将 Base64 转为 Blob，上传到服务端图片上传 API
3. 替换为返回的 URL
4. 并行上传，限制 3 并发（使用简单的信号量）
5. 上传失败保留 Base64 原值（至少能在浏览器中显示）

**上传 API:** 需确认现有图片上传端点（可能是 `/analytics/api/screens/upload` 或类似路径）

## 影响范围

- 新文件: `pages/screens/utils/resourceRestorer.ts`
- 后续被 ScreenHeader 导入和模板市场导入调用

## 验证
- [ ] 含 data:image Base64 的 spec 还原后，字段变为 /analytics/... URL
- [ ] 并发限制生效（不超过 3 个同时上传）
- [ ] 单张上传失败时保留 Base64 原值，其他图片仍正常还原
- [ ] 返回 restoredCount 和 errors

## 完成标准
- [ ] 函数通过上述验证
- [ ] 与 resourceInliner 互为逆操作
