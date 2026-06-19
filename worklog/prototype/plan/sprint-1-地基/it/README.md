# Sprint-1 集成检查清单（IT）

**Sprint**: 地基（脚手架/设计系统/mock/外壳）
**状态**: READY
**目的**: 验证 F1–F4 集成后，原型地基在 Chrome 95 下端到端立得住——工程可构建、设计系统可用、mock 契约可走、外壳+阶段轨+门户由项目数据驱动闭环。

## 证据存放

所有验证证据归档到 `worklog/prototype/plan/sprint-1-地基/it/evidence/`，按用例编号分目录：

```
it/evidence/
├── F1/        # 构建/Chrome 95 冒烟（dist 扫描、preview 截图、build 日志）
├── IT-01/     # 工程构建与 legacy 产物
├── IT-02/     # 设计系统目视回归
├── IT-03/     # CompactTable 分页行为
├── IT-04/     # mock 契约与开关切换
├── IT-05/     # 种子数据端到端读取
├── IT-06/     # 外壳布局 + Chrome 95 禁用特性扫描
├── IT-07/     # 项目切换 + 上下文持久化
├── IT-08/     # 阶段状态派生 + 状态点
├── IT-09/     # "你的下一步"引导卡导航
└── IT-10/     # 重置样例数据复位
```

证据形式：Playwright 截图（320/768/1024/1440 关键断点择取）、构建/扫描日志、控制台日志快照。

## 端到端验证项

| ID | 验证项 | 覆盖 Feature/Task | 通过判据 | 证据 |
|----|--------|-------------------|----------|------|
| IT-01 | 工程构建与 legacy 产物 | F1-T01/T02 | `pnpm install && pnpm build` 成功；产物开启 legacy（target=chrome95）；browserslist 含 `Chrome >= 95`；`@/*` 别名两侧解析 | 构建日志 → `evidence/IT-01/` |
| IT-02 | 设计系统目视回归 | F2-T01/T02 | 预览页渲染全部 token 与原子组件（Button/Card/Surface/StatusDot/SectionTitle）；取色/间距来自 token；hover/focus/active 可见且仅 transform/opacity | 截图 → `evidence/IT-02/` |
| IT-03 | CompactTable 分页行为 | F2-T03 | 默认每页 10 条；切换条数触发刷新并重置第 1 页；数值列 tabular-nums 等宽对齐 | 截图（切页前后）→ `evidence/IT-03/` |
| IT-04 | mock 契约与开关切换 | F3-T01/T02 | service 返回 `Promise<Result<T>>`（`status=SUCCESS`）且延迟可调；`VITE_USE_MOCK=1` 走 fixtures、`=0` 切 axios 路径（编译通过、不接后端）；切换零业务改动 | 控制台日志 + 编译日志 → `evidence/IT-04/` |
| IT-05 | 种子数据端到端读取 | F3-T03 | "销售准备项目"4 阶段种子（2 源/1 作业/1 数据集/1 指标，稳定 code 主键）经各域 service 全部可读；足以派生阶段状态 | 控制台/响应快照 → `evidence/IT-05/` |
| IT-06 | 外壳布局 + Chrome 95 禁用特性扫描 | F4-T01, F1-T02 | 外壳三区（顶栏/左轨/内容区）渲染，窗口缩放无错位；源码 CSS grep 无 `oklch(` / `:has(` / `@container` / `subgrid`；内容区 `<Outlet/>` 承载占位路由 | 截图 + grep 日志 → `evidence/IT-06/` |
| IT-07 | 项目切换 + 上下文持久化 | F4-T02 | 项目切换器列出项目并可切换；`currentProjectId` 写入 store；刷新后经 persist 保持；阶段数据按当前项目 scoped | 截图（切换+刷新后）→ `evidence/IT-07/` |
| IT-08 | 阶段状态派生 + 状态点 | F4-T03 | 阶段轨四阶段状态点按派生规则渲染（销售准备项目呈预期态：连接✓/集成●/资产○/指标○）；状态点取 F2 状态 token | 截图 → `evidence/IT-08/` |
| IT-09 | "你的下一步"引导卡导航 | F4-T04 | 门户引导卡文案匹配当前阶段；点击 CTA 导航到对应阶段占位路由；切项目后内容更新 | 截图（卡片+导航后）→ `evidence/IT-09/` |
| IT-10 | 重置样例数据复位 | F3-T04, F4-T03 | dev 入口触发 `resetSeedData()` 后 fixtures 回初始种子；外壳阶段状态/门户引导卡随之刷新到初始态；生产构建不暴露入口 | 截图（重置前后）+ 构建确认 → `evidence/IT-10/` |

## 黄金主线连通性自检（地基层）

本 sprint 不实现业务阶段页面，但需确认地基已为黄金主线就绪：

- [ ] 从项目门户的"你的下一步"引导卡可点击进入当前阶段（占位）路由——证明"统一入口→下一步"链路通。
- [ ] 阶段轨四阶段可见、状态点由真实种子派生——证明阶段向导承载面就位。
- [ ] 切换项目后整壳上下文、阶段状态、引导卡联动更新——证明工作空间 scoping 兑现。
- [ ] 重置样例数据后全壳复位——证明演示/验证可反复执行。

## Sprint 完成放行条件

- [ ] IT-01 ~ IT-10 全部通过，证据齐备归档。
- [ ] Chrome 95 冒烟（F1-T03 / IT-06）无 `SyntaxError`/`ReferenceError`/未降级特性报错。
- [ ] 源码层零 oklch / `:has()` / 容器查询 / subgrid。
- [ ] F1–F4 各自 README"完成标准"全部勾选。
- [ ] 地基可解锁 S2 连接 / S3 画布内核 / S5 资产 并行启动。
