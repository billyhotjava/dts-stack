# Sprint-77 集成验收

**状态**：SOURCE_PASS / UI_BLOCKED

| IT | 场景 | 必须断言 | 状态 | 证据位置 |
|---|---|---|---|---|
| IT-01 | 主题注册与路由解析 | 全 DTS 核心主题存在；建模路由命中正确主题；未知路由回退 | PASS | `evidence/it-01-registry/` |
| IT-02 | Header 与上下文 Sheet | 所有 Dashboard 布局可打开/关闭；标题、描述、ESC、焦点可用 | SOURCE_PASS / UI_BLOCKED | `evidence/it-06-quality/` |
| IT-03 | 完整帮助中心 | `/settings/help?topic=...` 可深链、搜索、切换主题 | SOURCE_PASS / UI_BLOCKED | `evidence/it-06-quality/` |
| IT-04 | 命令搜索 | Ctrl/Cmd+K 显示帮助主题并正确导航 | SOURCE_PASS / UI_BLOCKED | `evidence/it-06-quality/` |
| IT-05 | 建模页面减负 | 通用教程消失，动态状态/阻塞/恢复仍在 | PASS_SOURCE | `evidence/it-05-modeling-copy/` |
| IT-06 | 合并质量门 | 定向契约、`pnpm build`、`git diff --check`、GitNexus detect_changes | PASS | `evidence/it-06-quality/` |
| IT-07 | 浏览器 | 1366×768、390×844、Chrome95；无大屏右下角冲突 | BLOCKED | `it/baseline.md` |

IT-01～06 已按约定在编码后合并执行一次；IT-07 等待 F0 登录基线恢复。源代码通过不替代真实浏览器验收。
