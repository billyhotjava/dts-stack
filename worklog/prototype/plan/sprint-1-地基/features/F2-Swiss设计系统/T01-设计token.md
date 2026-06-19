# T01: 设计 token（色彩/字号/间距/网格）

**优先级**: P0
**状态**: READY
**依赖**: F1-T01

## 目标

定义 Swiss 设计系统的全部基础 token，以 CSS 变量形式落地，作为组件与布局的唯一取值来源。

## 技术设计

- **文件**：`src/ui/tokens.css`（`:root` 下 CSS 自定义属性），可选 `src/ui/tokens.ts` 暴露同名 TS 常量给 AntD `ConfigProvider` theme token 复用。
- **色彩（全部 HSL/hex，禁 oklch）**：
  - 中性灰阶：`--color-neutral-0..900`（背景/边框/文本分层），亮色为主。
  - 单一功能强调色：沉稳蓝 `--color-accent`（+ hover/active 派生）。
  - 语义色：`--color-success` / `--color-warning` / `--color-error` / `--color-info`。
  - 表面：`--color-surface` / `--color-surface-raised` / `--color-border`。
- **排版**：Inter（F1 已引入 `@fontsource-variable/inter`）；强字号层次 `--text-xs..--text-3xl`；行高 token；数据区约定 `font-variant-numeric: tabular-nums`（token `--font-numeric`）。
- **间距**：8px 基线刻度 `--space-1..--space-12`（4/8/12/16/24/32...）。
- **网格**：12 列内容网格 token（`--grid-columns: 12`、`--grid-gutter`），供布局类/工具类取值。
- **状态语言**：阶段/节点/运行状态点统一色 token（complete/active/pending/error），F4 阶段轨直接复用。
- **动效**：`--duration-fast/normal` + `--ease`；约定仅动 `transform`/`opacity`。

**Chrome 95 守则**：所有颜色用 `hsl()`/`hsla()`/hex；禁 `oklch()`；禁 `:has()`/容器查询/subgrid（布局靠 flex/grid + 工具类）。

## 影响范围

- 新增 `src/ui/tokens.css`（+ 可选 `src/ui/tokens.ts`）。
- 在 `main.tsx`/全局样式入口导入 tokens.css；AntD theme token 可映射部分变量。

## 验证

- [ ] tokens.css 加载后，CSS 变量在 DevTools `:root` 可见。
- [ ] grep 确认无 `oklch(`；颜色均为 hsl/hex。
- [ ] 8px 基线刻度、12 列网格 token 齐备。
- [ ] 状态色 token（complete/active/pending/error）就位，供 F4 复用。

## 完成标准

- [ ] 色彩/字号/间距/网格/状态/动效 token 全部以 CSS 变量落地。
- [ ] 全部 HSL/hex，零 oklch，符合 Chrome 95 约束。
- [ ] token 命名清晰、分组合理，可作为组件唯一取值源。
