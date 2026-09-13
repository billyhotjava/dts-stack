# T03: 登记抽屉与核验操作 UI

**优先级**: P0
**状态**: DRAFT（阻塞于 G0 的 B1 运行实例/账号、B3 Chrome 95、B5 写权限）
**依赖**: T01（登记/软失效契约）、T02（核验契约）

## 目标

治理员在影响分析页即可完成"补录 → 核验 → 失效"三个动作，每个动作有明确的四态反馈，无需离开当前上下文。

## 技术设计 (Contract-first)

### 输入契约（消费）

| 动作 | 契约 | 来源 |
|---|---|---|
| 登记 | `POST /api/catalog/lineage` | T01 |
| 软失效 | `DELETE /api/catalog/lineage/{id}?force=false` | T01 |
| 核验 | `PATCH /api/catalog/lineage/{id}/verification` | T02 |
| 数据集选项 | `listDatasets`（**注意**：账本#15 现为硬编码 300 条，本 Task 先复用 `loadDatasetOptions`，服务端搜索由 F4/T03 替换后自动生效） | `lineageShared.tsx:75` |

### 输出契约（前端状态）

- 登记成功 → 关闭抽屉 → 调用页面既有 `runSearch(false, page, size)` 重查（**不做乐观插入**，理由见 Feature README）。
- 核验成功 → 用响应体的 edge DTO 就地替换 `impact.edges` 中同 `id` 的元素（乐观更新 + 失败回滚）。
- 软失效成功 → 从当前 `edges` 移除该行 + toast「已失效，历史快照仍可查询」。

### 组件设计

新增 `pages/catalog/lineage/LineageDeclareDrawer.tsx`（登记抽屉）与 `pages/catalog/lineage/LineageVerifyAction.tsx`（行内核验/失效操作）。

放在 `pages/catalog/lineage/` 新目录而非塞进 `lineageShared.tsx`——后者已 486 行（账本#3），继续堆会突破项目 800 行上限。

### 状态与错误映射

| 后端错误码 | UI 表现 |
|---|---|
| `LINEAGE_SELF_LOOP` | 抽屉内 Alert「上游与下游不能是同一个数据集」，下游 Select 标红 |
| `LINEAGE_DUPLICATE` | 抽屉内 Alert「该血缘关系已存在」，附「查看现有关系」按钮定位到边表该行 |
| `LINEAGE_EDGE_EXPIRED` | toast「该关系已失效，无法核验」+ 刷新列表 |
| `RESOURCE_NOT_VISIBLE` | toast「无权限操作该数据集」 |
| 其余 | 交由全局拦截器（既有约定，页面不重复 toast） |

### 边表改造

在 `lineageShared.tsx` 的 `edgeColumns`（`:313`）末尾追加操作列。因 `edgeColumns` 为**共享常量**且被 `LineageDiffPage` 复用（`LineageDiffPage.tsx:108/111`），不能直接改常量——改为导出一个 `buildEdgeColumns({ actions })` 工厂：

- 影响分析页传 `actions`，渲染操作列；
- 快照对比页不传，列集合与现状完全一致（**零回归**）。

### 权限门控

无 `CATALOG_MAINTAINERS` 时：「登记血缘」按钮 `disabled` + Tooltip「需要目录维护权限」，操作列不渲染。权限来源沿用项目既有的权限 hook（实施时按账本外的既有约定接入，不新造权限体系）。

## UI 交互规格

线框、四态、走查见 F2 README。补充：

- 抽屉宽度 520（与 `LineageNodeDrawer` 一致，`lineageShared.tsx:458`），`destroyOnClose`。
- 表单用 antd `Form` + `rules`，提交前本地校验必填与自环，减少一次往返。
- 二次确认用 `Modal.confirm`，标题「确认失效该血缘关系？」，正文写明软失效语义。
- 分页遵循项目统一约定（默认 10 条/页、切换条数刷新），本 Task 不改边表分页配置。

## 影响范围

| 文件 | 改动 |
|---|---|
| `pages/catalog/lineage/LineageDeclareDrawer.tsx` | **新建** |
| `pages/catalog/lineage/LineageVerifyAction.tsx` | **新建** |
| `pages/catalog/lineageShared.tsx` | `edgeColumns` → `buildEdgeColumns({actions})` 工厂；保留同名常量导出以免影响 diff 页 |
| `pages/catalog/LineageImpactPage.tsx` | 卡片 `extra` 加「登记血缘」；边表接操作列；接抽屉 |
| `pages/catalog/LineageDiffPage.tsx` | 仅改为显式调用 `buildEdgeColumns()`（无 actions），行为不变 |
| `api/platformApi.ts` | 三个函数类型签名（T01/T02 已加） |
| `pages/catalog/lineage/lineageDeclare.source-contract.test.ts` | **新建** |

## 验证 (RED→GREEN)

### 契约/单测

- [ ] `buildEdgeColumns` 不传 actions 时列集合与既有 `edgeColumns` 完全一致（防 diff 页回归）
- [ ] 错误码 → 文案映射表全覆盖
- [ ] 无权限时按钮 disabled、操作列不渲染

### UI 走查（B1 关闭后执行）

1. 进影响分析，选样本数据集 → 记录当前边数 N
2. 点「登记血缘」→ 抽屉打开（**空态证据**）
3. 上游/下游选同一个 → 提交 → 见自环校验（**错误态证据**）
4. 改为合法上下游 → 提交 → loading（**加载态证据**）→ toast + 边数变 N+1（**成功态证据**）
5. 再次提交同一对 → 409 Alert「该血缘关系已存在」
6. 新边行点「核验 > 已核验」→ 填说明 → Tag 变绿
7. 点「失效」→ 二次确认 → 边数回 N
8. 筛选栏「快照时间」设为步骤 7 之前 → 该边重新出现
9. 切图谱页 → 渲染一致

## Definition of Done

- [ ] 架构：契约测试绿；diff 页边表零回归
- [ ] UI：登记抽屉与核验操作四态截图齐全，走查 9 步全通过
- [ ] 切片：IT-03、IT-04 在运行实例上完成
- [ ] Chrome 95 下 Drawer/Dropdown/Modal 正常（并入 F5/T02 一次性验收）
- [ ] 无占位证据
