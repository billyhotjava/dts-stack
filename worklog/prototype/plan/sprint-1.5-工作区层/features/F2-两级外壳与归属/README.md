# F2: 两级外壳与数据源归属

**优先级**: P0
**状态**: DONE

## 目标
顶栏升级为「工作区 ▸ 项目」两级；连接阶段体现"选源绑定"与混合制归属。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 工作区切换器 + 顶栏两级 + App 联动（工作区变更→重载项目） | P0 | DONE | F1 |
| T02 | 连接阶段归属页（平台共享源 / 本部门本地源 + 绑定到项目） | P0 | DONE | F1 |

## 实现落点
- `src/shell/WorkspaceSwitcher.tsx`、`TopBar.tsx`(工作区▸项目)、`App.tsx`(loadWorkspaces→currentWorkspaceId 变更 effect→loadProjects)、`DevReset.tsx`(重置重载两级)
- `src/stages/connect/ConnectStage.tsx`(按 scope 分组：平台共享/本部门本地，scope 过滤到当前工作区) + 路由替换 `/connect` 占位

## 完成标准
- [x] 切「质量处」→ 项目变「质量月报项目」、四阶段重新派生为完成
- [x] 连接页平台共享源(3) + 质量处本地源(1)，不串其它部门本地源
