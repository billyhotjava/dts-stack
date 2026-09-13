# Sprint-57 F3-T03 基础数据三页面范式巡检

巡检时间：2026-07-04  
范围：数据元 `ElementsPage`、业务术语 `GlossaryPage`、公共码表 `ReferenceCodesPage`

## 结论

三页面已统一基础列表范式：`CompactTable`、默认 10 条/页、`10/20/50/100` 切换、切换 page size 回第 1 页、总数展示、`EmptyState`、`useGovernanceManageAccess` 权限置灰、错误 toast 提示。

## 巡检矩阵

| 项目 | 数据元 | 业务术语 | 公共码表 | 结论 |
|------|--------|----------|----------|------|
| 分页 | `CompactTable`，服务端分页，默认 10，size 选项 10/20/50/100，切 size 回第 1 页 | `CompactTable`，本地分页，默认 10，size 选项 10/20/50/100，切 size 回第 1 页 | `CompactTable`，服务端分页，默认 10，size 选项 10/20/50/100，切 size 回第 1 页 | 一致 |
| 空态 | `EmptyState`，页面右上保留下载模板/导入/新增入口 | `EmptyState`，页面右上保留导出/新增入口 | `EmptyState`，页面右上保留同步 seeds/新增入口 | 一致 |
| 加载/错误 | 表格 loading；接口异常 toast | 表格 loading；详情分段 loading；接口异常 toast | 表格 loading；运维概览/导入历史 loading；接口异常 toast | 一致 |
| 权限 | `useGovernanceManageAccess`；维护动作置灰 | `useGovernanceManageAccess`；维护动作置灰 | `useGovernanceManageAccess`；维护动作置灰 | 一致 |
| 搜索 | 关键词搜索；重置回第一页 | 关键词搜索；重置回第一页 | 关键词搜索；重置回第一页 | 一致 |
| 详情/引用 | 详情 Drawer + 引用追溯 | 详情 Drawer + 版本/评审/引用追溯 | 引用追溯 Modal；目录/码值/映射拆分为多操作面板 | 有豁免 |

## 豁免

- 公共码表引用追溯暂保留 Modal：该页同时承担目录、码值、映射、结构化导入、导入历史多段操作，直接切 Drawer 会扩大交互回归面。后续如要统一 Drawer，应单独作为码表页重构切片处理。
- 状态筛选暂不强行统一：数据元、业务术语、公共码表的状态语义不完全一致，当前以关键词检索作为共同最小交互；状态筛选待后续按对象语义补充。
- 空态引导动作不嵌入 `EmptyState` 内部：三页均在页面右上角保留主要动作，避免空态和列表态按钮位置漂移。

## 截图证据

- `assets/it-8-f3-elements-page.png`
- `assets/it-8-f3-glossary-page.png`
- `assets/it-8-f3-reference-codes-page.png`
