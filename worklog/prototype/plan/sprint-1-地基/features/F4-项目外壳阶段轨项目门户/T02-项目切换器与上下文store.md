# T02: 项目切换器 + 项目上下文 store（zustand）

**优先级**: P0
**状态**: READY
**依赖**: T01, F3-T03

## 目标

实现顶栏项目切换器与项目上下文 store，让"当前项目"成为贯穿全应用的上下文，所有阶段数据 scoped 到当前项目——兑现工作空间范式的连贯性。

## 技术设计

- **store**：`src/store/contextStore.ts`，zustand + `persist`（`createJSONStorage(() => localStorage)`），对齐现网 `source/dts-platform-webapp/src/store/contextStore.ts` 命名与形态。
  - state：`currentProjectId`、项目列表（或经 service 拉取）、`setCurrentProject(id)`。
  - 持久化 `currentProjectId`（刷新保持上下文）。
- **项目数据来源**：`src/mock/services/projectService.ts`（F3）读 fixtures 项目列表（含"销售准备项目"）。
- **项目切换器**：`src/shell/ProjectSwitcher.tsx`，顶栏下拉（AntD `Select`/`Dropdown`），切换写入 store。
- **scoping 约定**：阶段 service 调用以 `currentProjectId` 为入参；本 sprint 数据只有一个种子项目，验证 scoping 机制即可（可加一个空壳第二项目演示切换）。
- **不可变更新**：store action 用不可变方式更新（对齐编码规则）。

## 影响范围

- 新增 `src/store/contextStore.ts`、`src/shell/ProjectSwitcher.tsx`；`src/mock/services/projectService.ts` 提供项目列表。
- 被 T03（阶段状态按当前项目派生）、T04（"下一步"按当前项目）消费。

## 验证

- [ ] 项目切换器渲染于顶栏，列出项目（含"销售准备项目"）。
- [ ] 切换项目写入 store，`currentProjectId` 更新。
- [ ] 刷新页面后当前项目经 persist 保持。
- [ ] 阶段数据查询以 `currentProjectId` 为作用域。

## 完成标准

- [ ] 项目上下文 store（zustand+persist）对齐现网 `contextStore`，持久化当前项目。
- [ ] 项目切换器可用，切换贯穿全应用上下文。
- [ ] scoping 机制就位，store 更新不可变。
