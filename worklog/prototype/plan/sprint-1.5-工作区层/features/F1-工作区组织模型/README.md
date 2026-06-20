# F1: 工作区组织模型（类型/mock/store）

**优先级**: P0
**状态**: DONE

## 目标
落地三层组织模型的数据与状态层：工作区实体、项目归属工作区、数据源 scope 归属，以及按工作区级联的 store。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 类型：Workspace + Project.workspaceId + DataSource(scope) | P0 | DONE | S1 |
| T02 | mock：workspaces/dataSources 种子 + db + workspaceService/dataSourceService | P0 | DONE | T01 |
| T03 | workspaceStore + projectStore（按 workspaceId 级联加载） | P0 | DONE | T02 |

## 实现落点
- `src/types/workspace.ts`、`types/project.ts`(+workspaceId)、`types/datasource.ts`(scope: platform|workspace)
- `src/mock/fixtures/{workspaces,projects,dataSources}.ts`、`mock/db.ts`、`mock/services/{workspaceService,projectService,dataSourceService}.ts`
- `src/store/workspaceStore.ts`(persist currentWorkspaceId)、`projectStore.ts`(listByWorkspace + 旧项目不在新工作区则回落首个)

## 完成标准
- [x] 项目按工作区过滤；切工作区回落到该区首个项目
- [x] 数据源 availableFor(workspaceId) = 平台共享 + 本部门本地
