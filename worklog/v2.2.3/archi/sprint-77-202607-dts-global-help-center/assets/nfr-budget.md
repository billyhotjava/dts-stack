# Sprint-77 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` 与 DTS Chrome 95/菜单权限不变量
**适用范围**：全局帮助入口、静态主题注册表、完整帮助页和命令搜索

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 兼容性 | Chrome 95；不得依赖更高版本浏览器 API | `pnpm build` + Chrome95 页面 smoke | F2/T02 | BUILD_PASS / UI_BLOCKED |
| 网络/超时 | 打开 Sheet、搜索和切换主题产生 0 个新增网络请求 | 浏览器网络记录断言帮助交互请求数为 0 | F1/T02 | SOURCE_PASS / UI_BLOCKED |
| 可访问性 | 帮助按钮至少 40×40px；具备 `aria-label`；Sheet 必须有 Title/Description；ESC 可关闭 | source-contract + 键盘 UI 走查 | F1/T02 | SOURCE_PASS / UI_BLOCKED |
| 响应式 | 桌面 Sheet 最大约 480px；390px 视口全宽；Header 无横向溢出 | 1366×768、390×844 截图与 `scrollWidth <= clientWidth` | F2/T02 | SOURCE_PASS / UI_BLOCKED |
| 权限/可见性 | 继承登录壳；新增菜单、角色、API、数据库均为 0 | source-contract 检查静态路由与 Header；`git diff --name-only` 检查后端/seed 变更为 0 | F2/T02 | PASS |
| 失败模式 | 未知 pathname/topic 必须回退 `getting-started`，不能白屏 | `helpTopics` 单元测试 | F1/T01 | PASS |
| 搜索稳健性 | `[`、`(`、`\` 等正则特殊字符不得导致渲染异常 | SearchBar 契约/单元测试 | F1/T03 | PASS |
| 安全 | 不使用 `dangerouslySetInnerHTML`；不加载外部帮助内容；不匿名发布 PDF | source-contract + `rg` 定向断言 | F2/T02 | PASS |
| 数据量级 | 首批静态主题不超过 20 个；无分页/数据库需求 | 单元测试断言 `HELP_TOPICS.length <= 20` | F1/T01 | PASS（15） |
| 查询效率/索引/事务/幂等 | N/A——无 API、数据库、写操作 | — | — | N/A |
| 审计 | N/A——帮助浏览为只读且不新增业务动作 | — | — | N/A |

## 未达标项处置

| 缺口 | 影响 | 处置 | 关联 Task |
|---|---|---|---|
| 当前登录与浏览器基线不可用 | Chrome95、网络请求和响应式适应度函数暂不能执行 | F0 恢复后补 IT-02/03/04/07；未补前 Sprint 不得 DONE | F0/T01 |
