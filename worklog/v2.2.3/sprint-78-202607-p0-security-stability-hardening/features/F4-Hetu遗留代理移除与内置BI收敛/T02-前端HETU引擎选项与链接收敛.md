# T02：前端 HETU 引擎选项与链接收敛

**优先级**：P0
**状态**：READY
**依赖**：T01（代理面已移除，前端不再生成死链）

## 目标

platform-webapp 中 HETU 引擎选项与指向 hetu 路径的入口全部移除，内置 `/bi` 成为唯一分析入口；历史深链重定向保留。

## 技术设计（Contract-first）

- **输入契约**：既有 BI 链接数据（引擎字段）；`biLinkUrl.ts` 的 `shouldRedirectHetuEntryToAnalytics`（账本#15）。
- **输出契约**：引擎选项集合不含 HETU；链接生成只产出内部 `/bi` 路由；历史 hetu 深链命中重定向到 `/bi`（兼容层保留）。
- **数据流**：页面渲染引擎选项（收敛后集合）→ 用户点击 → 内部路由；旧书签 → 重定向判定 → `/bi`。
- **错误路径**：存量数据中 engine=HETU 的记录 → 前端按内置 BI 渲染并提示已迁移（不报错阻断）；后端如存在引擎枚举校验，实施期一次 grep 确认是否需同步放宽。
- **复用点**：账本#15 的重定向判定；既有菜单/路由注册（不新增菜单）。
- **实现方案**：
  1. 定位 HETU 引擎选项注册点（评审提及 BiLinksPage，实施期一次 grep 精确定位）并移除。
  2. 收敛 `biLinkUrl.ts`：保留重定向函数，删除/简化只为 hetu 服务的 URL 构造分支。
  3. 检查菜单、帮助文案（Sprint-77 helpTopics）中 hetu/大屏旧路径引用并更新。
  4. 更新对应 source-contract 测试（frontend-product-consistency：文案与既有页面一致）。
- **禁止**：删除 `shouldRedirectHetuEntryToAnalytics` 兼容重定向；新增任何指向已移除路径的链接。

## 影响范围

- `source/dts-platform-webapp/src/utils/biLinkUrl.ts`
- BI 链接页面组件（实施期定位）
- 相关 source-contract 测试、Sprint-77 helpTopics（如引用旧路径）

## 验证（RED→GREEN）

- [ ] 前端 `grep -rni "hetu" source/dts-platform-webapp/src` 仅剩兼容重定向注释/判定。
- [ ] source-contract 测试更新并通过；`pnpm build` 通过。
- [ ] 引擎选项渲染断言（contract 测试）：无 HETU。

## Definition of Done

- [ ] 架构：contract 测试 + build 绿
- [ ] UI：BI 链接页面无 HETU 选项（浏览器证据按基线 GAP 记录，source-contract 先行）
- [ ] 切片：证据入 `it/evidence/it-06-hetu-removal/`
- [ ] 无占位证据
