# 生产构建证据

- 命令：`pnpm build`
- 模式：`LEGACY_BROWSER_BUILD=1`
- 结果：退出码 `0`，`10583 modules transformed`，`built in 2m 6s`
- 结论：TypeScript、Vite legacy 转译和生产 chunk 生成全部通过。

非阻断基线警告：`caniuse-lite` 数据较旧；少数组件 chunk 超过 `1500 kB`。两者均为既有构建治理项，本次未引入构建失败或 Chrome 95 语法错误。
