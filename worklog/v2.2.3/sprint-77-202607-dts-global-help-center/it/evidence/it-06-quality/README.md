# IT-06 合并质量门证据

**结果**：PASS（真实 UI 证据除外）

## 一次性验证

2026-07-28 在 `source/dts-platform-webapp` 执行：

```bash
node --test --experimental-strip-types \
  src/features/help-center/helpTopics.test.ts \
  src/features/help-center/helpCenter.source-contract.test.ts \
  src/layouts/components/search-bar.help.source.test.ts \
  src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts \
  src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts \
  src/pages/modeling/metricWorkbench.source-contract.test.ts
```

结果：39/39 通过，0 失败。

```bash
pnpm build
```

结果：TypeScript 编译与 Vite legacy 生产构建成功，退出码 0。仅有既存 Browserslist 数据陈旧和大 chunk 警告；新帮助页为独立小 chunk。

```bash
git diff --check -- <Sprint-77 paths>
```

结果：退出码 0。对未跟踪的新文件另执行定向尾随空白检查并通过。

## 评审与影响

- GitNexus 编辑前对 Header、SearchBar、静态路由及六个建模组件逐项影响分析，均为 LOW，无 HIGH/CRITICAL。
- 完成后 `detect_changes(scope=unstaged)` 的 overall risk 为 LOW，未识别受影响执行流程。
- 当前工作区原本已有大量未提交用户改动，因此 GitNexus 的 73 文件/192 symbols 是整个 dirty worktree，不代表 Sprint-77 独占范围；本次用路径清单收敛并保留所有无关修改。
- 代码评审发现的 SQL 帮助迁移缺口、Sheet 初始焦点和移动搜索可访问名称均在最终验证前修复。

## 回滚边界

仅需撤销 Sprint-77 清单内的前端与 worklog 文件；无后端、菜单 seed、数据库迁移、配置或数据回滚动作。未执行提交或部署。
