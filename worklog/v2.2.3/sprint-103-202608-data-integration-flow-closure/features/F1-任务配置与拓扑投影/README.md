# F1：任务配置与拓扑投影

**优先级**：P0
**状态**：`IMPLEMENTED_AND_DEPLOYED`
**依赖**：F0 完成

## 目标

让数据开发人员通过类型化步骤配置一个明确接入任务及其唯一目标资产，并从同一份草稿/版本得到自动拓扑和服务端校验；消除全局本地画布、完整 DTO 覆盖、跨资产质量绑定和双事实源。

## 契约定义

| 类型 | 契约 | 关键字段/行为 |
|---|---|---|
| REST | `GET/PUT /api/ingestion/tasks/{id}/design` | `If-Match`、类型化配置、`destination.assetRef`、`postIngestionQuality`、`planChecksum`、409 |
| REST | `POST .../{id}/design/validate` | `ValidationReport`，稳定 field/path/code/message |
| REST | `GET .../{id}/topology?view=` | 只读 `TopologyProjection`，与 plan checksum 一致 |
| 数据 | 既有 task/revision snapshot | 不新建 workflow 配置 owner |
| 兼容 | `graphDsl` | 历史只读/导出，不驱动新页面或执行 |

## UI/UX 规格

- **入口**：保持 `/explore/etl/orchestration`，标题与菜单文案改为“数据集成流程”。
- **布局**：页头任务选择与状态；设计 Tab 左侧四步表单、右侧只读拓扑；运行 Tab 复用 F3。
- **空态**：未选任务时展示任务选择和“新建接入任务”，不显示空白可编辑画布。
- **加载态**：表单与拓扑骨架屏共享同一个 taskId 请求上下文。
- **错误态**：403/404/登录过期/网络失败不保留上一任务内容；校验错误定位到步骤和字段。
- **成功态**：显示已保存时间、revision state、plan checksum 和“拓扑与配置一致”。
- **关键交互**：保存草稿、校验、重置本次未保存输入；拖拽、连线和任意节点新增不属于首版。
- **质量表达**：第四步固定为“接入后质量验证”；只能作用于第三步已确认的目标资产，不能独立挑选其他策略/数据集。
- **兼容**：Chrome 95；页面改造同步 source-contract test；复用现有任务配置组件。

## Tasks

| Task | 状态 | 依赖 | 产出 |
|---|---|---|---|
| [T01-收敛任务配置与并发保存](T01-收敛任务配置与并发保存.md) | IMPLEMENTED_AND_DEPLOYED | F0/T02 | task-scoped design API、步骤表单、If-Match、脏数据保护 |
| [T02-生成拓扑投影与服务端校验](T02-生成拓扑投影与服务端校验.md) | IMPLEMENTED_AND_DEPLOYED | F1/T01 | server validation、只读 topology、错误定位 |

## Definition of Ready

- [ ] F0/T02 已冻结 design/topology DTO、错误码和现有字段映射。
- [ ] 任务配置组件复用点与需要抽取的文件边界明确。
- [ ] 菜单种子、动态路由和 Chrome 95 验收入口已确认。
- [ ] plan checksum 的规范化字段集合和兼容策略已冻结。
- [ ] 新建目标对象如何在设计期获得稳定 datasetId、无规则时如何降级已冻结。

## 完成标准

- [ ] 页面不存在可编辑图与任务配置两份业务语义。
- [ ] task A → task B → task A、空配置、登录过期和双会话冲突均有测试。
- [ ] 表单、拓扑、校验响应、revision 使用相同 taskId 与 plan checksum。
- [ ] destination assetRef、qualityPolicyRef 与拓扑资产/质量节点指向同一 datasetId。
- [ ] 真实用户在 Chrome 95 完成配置、保存、校验的四态走查。
