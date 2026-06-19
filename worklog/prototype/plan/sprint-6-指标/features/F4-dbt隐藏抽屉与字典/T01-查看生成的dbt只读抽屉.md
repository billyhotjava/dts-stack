# T01: 「查看生成的 dbt」只读抽屉

**优先级**: P1
**状态**: READY
**依赖**: S4

## 目标

落地阶段④的「查看生成的 dbt」**只读抽屉**：消费 S4 F4 在画布上生成的 dbt 映射，只读呈现底层 dbt model，**不提供任何文件树式编辑/写入口**。把现网顶层 `DbtFileBrowser`/`ModelPipeline` 菜单入口下沉为此抽屉。这是普通用户与 dbt 唯一的、被动唤起的、只读接触点。

## 技术设计

- 文件：`app/src/stages/metrics/DbtDrawer.tsx`（只读抽屉组件，命名呼应现网 `DbtFileBrowserPage` 但形态降级为抽屉）；唤起入口分散在 F2 `SemanticPublishPage`、F3 `ModelPipelinePage`、F1 指标详情等处的「查看生成的 dbt ▸」次级按钮。
- **顶层菜单移除**：阶段④导航与平台旁路区**均不**注册 dbt 文件浏览/流水线为独立菜单项；仅保留抽屉唤起按钮。这是「dbt 下沉」的兑现点。
- 抽屉内容（**全部只读**）：
  - 左侧：本项目已生成的 dbt model 列表（来源 = S4 F4 画布生成映射 + F2/F3 发布产物标记），按业务实体分组。
  - 右侧：选中 model 的只读详情——编译后的 SQL（只读代码块）、来源映射（哪个画布节点/语义模型/SQL 模型生成它）、lineage 摘要（轻量占位）。
  - **禁止出现**：新建/编辑/删除/重命名/运行/保存 任何按钮，无文件树编辑、无 inline 编辑、无右键菜单写操作。
- 用 Ant Design `Drawer`（右侧滑出），Swiss token；代码块只读高亮；固定栏 flex + ResizeObserver，**禁 `:has()`/容器查询**。仅 `transform/opacity` 动效。
- mock service：`dbtService`（**只读契约**：`listGeneratedModels` / `getGeneratedModel` 返回 `Promise<Result<T>>`，消费 S4 F4 生成映射 fixtures + F2/F3 发布产物标记；**不提供** create/update/delete/run 方法，确保只读语义从契约层就成立）。

## 影响范围

- 新增 `app/src/stages/metrics/DbtDrawer.tsx`（只读抽屉）
- 在 F1/F2/F3 相关页面挂「查看生成的 dbt ▸」唤起按钮
- 新增 `app/src/mock/services/dbtService.ts`（仅只读方法 `listGeneratedModels`/`getGeneratedModel`）
- **移除/不注册** dbt 文件浏览/ModelPipeline 的顶层菜单项（导航配置层）
- mock fixtures：销售准备项目「销售达成率」对应的生成 dbt model（来源映射指向 S4 画布节点 + F2 语义模型）

## 验证

- [ ] dbt **不在任何顶层/旁路区菜单出现**；只能经 F1/F2/F3 的「查看生成的 dbt ▸」按钮被动唤起抽屉。
- [ ] 抽屉内 model 列表与详情**完全只读**：无新建/编辑/删除/运行/保存按钮，无文件树编辑、无右键写操作。
- [ ] 抽屉内容**消费 S4 F4 生成映射**——可看到 model 与画布节点/语义模型的来源对应关系。
- [ ] `dbtService` 契约层**只暴露只读方法**（无 create/update/delete/run）。
- [ ] Chrome 95：无 oklch/`:has()`/容器查询/subgrid；legacy 构建产物可加载。

## 完成标准

- [ ] 「查看生成的 dbt」只读抽屉可被动唤起、只读呈现生成产物，且消费 S4 生成映射。
- [ ] dbt 顶层菜单入口确认已下沉移除，普通用户主流程不触达 dbt 编辑。
- [ ] 全部经只读 `dbtService` 取数，无硬编码 dbt 内容散落组件内。
