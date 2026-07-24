# T05：拆分 SQL/dbt 页面并锁定候选上下文

**优先级**：P0
**状态**：READY
**依赖**：T03、T04

## 目标

让 SQL/dbt 页面回归高级编辑与执行器职责，并保证从工作台进入、保存、构建和返回时不丢失 canonical 候选上下文。

## 技术设计

- 移除 build 后自动 submit/approve/publish 链。
- context 固定 `planId/candidateId/modelSpecId/revision/checksum`，页面加载时服务端复验。
- 保存导致 checksum 变化时明确提示候选 STALE，并返回候选更新流程。
- 返回链接恢复工作台候选、条目和展开区域。

## 影响范围

- 修改 `SqlModelingPage.tsx`
- 修改 `sqlModelReleaseSubmit.helpers.ts`
- 新增 `delivery/useCanonicalDeliveryContext.ts`
- 修改 `ModelSpecDetailPage.tsx`
- 新增 navigation/source-contract tests

## 实施步骤

1. 先写禁止自动审批发布、旧 revision fail closed 和返回链测试。
2. 抽离上下文 hook，再删除页面内发布控制面逻辑。
3. 运行 modeling tests、`pnpm build` 和 Chrome 95 回归。

## 完成标准

- [ ] SQL/dbt 页面只产生 artifact/build 证据，审核发布回到计划工作台。
- [ ] 深链刷新、后退和多标签页不能把证据写入错误候选。
- [ ] **UI 实现验收**：页面固定显示 plan/candidate/model/revision 上下文条；保存造成漂移时出现 STALE 提示，并可一键返回工作台更新候选后恢复原编辑位置。
