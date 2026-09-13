# F0 前端页面承载面完善与重构

**状态**: DONE  
**目标**: 先把现有数据开发中心相关页面整理成可承载标准/dbt 联动的真实工作台，不新增菜单或页面。

## Tasks

| Task | 内容 | 状态 | 代码/证据 |
|------|------|------|-----------|
| T01 | 收敛数据开发中心入口到现有 SQL 建模页和 dbt 文件浏览页 | DONE | `static-routes.tsx`、`dynamic-resolver.tsx` source-contract |
| T02 | SQL 建模页保留 compile/test/build/release 主按钮并补标准承载区 | DONE | `SqlModelingPage.tsx`、`modelingToolbar.helpers.test.ts` |
| T03 | 数据元页增加模型字段引用语义和稳定控件标识 | DONE | `ElementsPage.tsx` |
| T04 | 公共码表页增加 dbt Seeds 同步状态和稳定控件标识 | DONE | `ReferenceCodesPage.tsx` |
| T05 | dbt 文件浏览页作为证据面，发布回到 SQL 建模页 | DONE | `DbtFileBrowserPage.tsx` |

## 验收

- `pnpm exec tsx --test src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/pages/modeling/modelingToolbar.helpers.test.ts`
- `pnpm build`
