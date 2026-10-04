# T02: normalizeScreenConfigV2 / validateScreenConfigV2

**优先级**: P0
**状态**: DONE（2026-04-21）
**依赖**: T01

## 执行纪要

- 新建 `src/analytics/pages/screens/v2/schema.ts`
- `normalizeScreenConfigV2` 宽容读取：schemaVersion 非 2 / layout 非对象 / cols 非法 / 组件 id 缺失或重复 / x+w 越界 / type 缺失 / 负坐标 均有兜底
- `validateScreenConfigV2` 严格校验：返回错误清单，空数组表示通过
- id 生成复用项目既有 `crypto.randomUUID()` 模式（Chrome 92+ 支持）
- 单元测试 `schema.test.ts` — **16 个 case 全部通过**
  - `isScreenConfigV2` / `createEmptyScreenV2` / normalize 6 case / validate 4 case

## 目标

提供"读 + 写"都会经过的标准化 + 校验函数，任何外部输入（后端返回、URL 参数、本地草稿）都先 normalize 再使用。

## 技术设计

### API

```ts
export interface NormalizeResult {
  config: ScreenConfigV2
  warnings: string[]
}

/**
 * 宽容读取：对缺省值补默认、数值类型修正、组件 id 补齐、layout 裁剪到 cols 范围内。
 * 不抛错；有问题写入 warnings。
 */
export function normalizeScreenConfigV2(raw: unknown, opts?: { id?: string }): NormalizeResult

/**
 * 严格校验：返回错误列表（空表示通过）。
 * 用于保存前 / 发布前门禁。
 */
export function validateScreenConfigV2(config: ScreenConfigV2): string[]
```

### normalize 规则

1. `version` 缺 → 补 `2`（如果能从结构推断）
2. `layout.cols` 缺或 ≤ 0 → 回落到 12
3. `layout.gap` 缺 → 12
4. 组件 `id` 缺 → `nanoid()` 生成
5. 组件 `layout.x/y/w/h` 非数字 → 0 / 0 / 2 / 2
6. 组件 `layout.w > cols` → 裁剪到 cols
7. `visible` 缺 → true

### validate 规则

- 组件 id 唯一
- 组件 `layout.x + w <= cols`
- 组件 `layout.y >= 0`
- 没有零尺寸（`w===0 || h===0`）
- 组件 `type` 存在于注册表
- 组件 overlap 允许（grid layout 支持重叠），但警告

## 影响范围

- 新增 `src/analytics/pages/screens/v2/schema.ts`
- 单元测试 `src/analytics/pages/screens/v2/__tests__/schema.test.ts`
- ResponsiveScreenLayout / Editor 从后端收到配置时先 normalize

## 验证

- [ ] 单元测试覆盖：
  - 缺省值补齐
  - 非法输入不抛错
  - 严格校验能捕获重复 id / 越界 w
- [ ] `pnpm test v2/schema` 全绿

## 完成标准

- [ ] normalize + validate 导出并被 F1-T02 调用
- [ ] 单元测试通过
