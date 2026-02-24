# P3-00 Chrome 95 兼容性基线强化

`status`: `planned`
`priority`: `P3`
`inspiration`: `现场交付要求 Chrome 95 最低兼容 + P0-05 遗留项`

## 目标

确保大屏设计器全链路（编辑 / 预览 / 公开页 / 导出）在 Chrome 95 下无功能障碍和视觉异常，为后续所有 P3 新增功能建立兼容性门禁。

## 背景

- Vite 构建目标已配置 `chrome95`（`LEGACY_BROWSER_BUILD=1`）。
- P0-05 已修复：剪贴板 fallback、backdrop-filter 降级、z-index 兼容、菜单黑底等。
- 本次新增的优化引入了 `crypto.randomUUID()`（Chrome 92+ ✅）、`String.prototype.replaceAll()`（Chrome 85+ ✅），均兼容。
- 需排查并建立持续门禁，防止后续开发引入不兼容 API。

## Chrome 95 不可用 API 清单

| API | 引入版本 | 处理策略 |
|-----|---------|---------|
| `structuredClone()` | Chrome 98 | 禁止直接使用，用 `JSON.parse(JSON.stringify())` 或 lodash `cloneDeep` |
| `AbortSignal.timeout()` | Chrome 117 | 禁止直接使用，用 `setTimeout` + `AbortController.abort()` |
| `CSS @container` | Chrome 105 | 禁止使用，用 `@media` 替代 |
| `Array.prototype.findLast()` | Chrome 97 | 禁止直接使用，用 `[...arr].reverse().find()` |
| `CSS :has()` | Chrome 105 | 禁止使用 |
| `navigator.clipboard` (HTTP) | 仅 HTTPS | 已有 `writeTextToClipboard` fallback ✅ |

## 子任务

### 1. ESLint 禁用规则

**文件**: `.eslintrc.cjs` 或 `eslint.config.js`

- 新增 `no-restricted-globals` 规则禁止 `structuredClone`。
- 新增 `no-restricted-properties` 规则禁止 `AbortSignal.timeout`。
- 确保 CI 阶段拦截不兼容 API 引入。

### 2. 全量扫描现有代码

- 扫描 `src/pages/screens/**` 中所有 `.ts/.tsx/.css` 文件：
  - `structuredClone` → 替换为 JSON 深拷贝
  - `AbortSignal.timeout` → 替换为手动超时
  - `@container` → 替换为 `@media`
  - `Array.prototype.findLast` → 替换为兼容写法
  - `:has()` 伪类 → 替换为 class 切换

### 3. Vite 构建配置固化

**文件**: `vite.config.ts`

- 当前逻辑已正确（生产默认 `chrome95`），确认 CI 环境变量传递无遗漏。
- 新增注释说明 Chrome 95 下限原因。

### 4. CSS 变量兜底检查

- 扫描所有 CSS 中的 `var(--*)` 使用，确保有 fallback 值。
- 特别关注：`backdrop-filter`、`gap`（Flexbox gap Chrome 84+ ✅）、`aspect-ratio`（Chrome 88+ ✅）。

### 5. 兼容性回归测试清单

建立手工回归清单（后续可自动化）：

| 场景 | 检查项 |
|------|-------|
| 编辑器加载 | 组件库面板 / 属性面板 / 图层面板正常显示 |
| 拖拽组件 | 从组件库拖入画布成功 |
| 属性编辑 | 颜色选择器、下拉框、滑块正常交互 |
| 撤销/重做 | Ctrl+Z / Ctrl+Y 正常工作 |
| 预览页 | 缩放控件、滚动、设备切换正常 |
| 公开页 | UUID 链接可访问，自适应缩放 |
| 导出 | PNG/PDF 导出不报错 |
| 菜单/弹窗 | 无黑底、无透明异常 |
| 图表渲染 | ECharts 图表正常显示 |
| DataV 装饰 | 边框/装饰动画正常 |

## 验收标准

- 全量扫描无 Chrome 95 不兼容 API。
- ESLint 规则阻止新引入不兼容 API。
- Chrome 95 回归测试清单全部通过。
- `bun x tsc --noEmit` 与 `bun run build` 均通过。

## 风险与回滚

- 风险：ESLint 规则误杀第三方库内部使用。
- 回滚：规则仅覆盖 `src/pages/screens/**`，不影响第三方包。

## 对后续 P3 任务的约束

**所有 P3 新增功能必须通过 Chrome 95 兼容性检查**，具体要求：
- 不使用上述禁用 API 清单中的任何 API。
- CSS 新增属性需确认 Chrome 95 支持或提供 fallback。
- 提交前在 Chrome 95 UA 模拟下验证核心流程。
