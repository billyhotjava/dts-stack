# F1：全局帮助入口与主题中心

**优先级**：P0
**状态**：IN_PROGRESS

## 目标

已登录用户从任意 Dashboard 页面打开当前页帮助，并可进入覆盖整个 DTS 的完整帮助中心。

## 契约定义

```ts
type HelpTopic = {
  id: string;
  section: string;
  title: string;
  summary: string;
  routePatterns: string[];
  keywords: string[];
  prerequisites: string[];
  steps: string[];
  blockers: Array<{ title: string; resolution: string }>;
  relatedTopicIds: string[];
};

resolveHelpTopic(pathname: string, search?: string): HelpTopic;
buildHelpTopicHref(topicId: string): string;
```

无 API、Service、数据和迁移契约。

## UI/UX 规格

```text
Dashboard Header                           完整帮助中心
[搜索] [?] [账户]                         [搜索帮助________]
          │                               ┌主题导航┐ ┌任务内容──────┐
          └─右侧 Sheet                    │DTS总览│ │前置/步骤/阻塞│
            当前页主题                    │建模   │ │相关主题       │
            前置条件                      └───────┘ └──────────────┘
            操作步骤
            常见阻塞
            [打开完整帮助中心]
```

- 空态：搜索无结果时提示“未找到帮助主题”并允许清空。
- 加载态：本地静态内容，无异步加载态。
- 错误态：未知 topic 自动回退总览，不抛白屏。
- 成功态：当前路由命中主题，可查看步骤并深链完整页面。
- 可访问性：按钮 `aria-label`；Sheet 有 Title/Description；支持 ESC；移动端全宽。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 建立主题契约与路由映射 | DONE | - |
| T02 | 实现 Header 入口、Sheet 与完整页面 | IN_PROGRESS | T01 |
| T03 | 接入 Ctrl/Cmd+K 帮助搜索 | DONE | T01/T02 |

## Definition of Ready

- [x] 契约已钉死
- [x] UI→本地注册表→静态路由竖线已画通
- [x] Header、Sheet、SearchBar、静态路由复用点已命名
- [x] 无后端/数据依赖
- [x] 验收映射到 IT-01～04
