# F6：专业模块交接与集成验收

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：F2-F5
**目标**：证明简化主线不是孤立页面演示，而是能够把模型交给标准、SQL/dbt、质量、发布、指标、资产和运维，并提供可回滚交付证据。

## Task

| Task | 优先级 | 状态 | 依赖 | 输出 |
|---|---|---|---|---|
| [T01-接通标准与指标专业模块](T01-接通标准与指标专业模块.md) | P0 | IN_PROGRESS | F3-T05、F5-T04 | 四类标准 owner + 指标 owner 等价接管 + standards/metrics reference handoff |
| [T02-接通构建发布运行与血缘证据](T02-接通构建发布运行与血缘证据.md) | P0 | DONE | F3-T05、F4、F5-T03 | artifact/release/run/lineage loop |
| [T03-完成双起点与兼容Chrome95验收](T03-完成双起点与兼容Chrome95验收.md) | P0 | DONE | T01/T02、F4-T04 | E2E/Chrome95 证据 |
| [T04-完成发布回滚与最终评审](T04-完成发布回滚与最终评审.md) | P0 | DONE | T03、F5-T04 | Go/No-Go 与退出清单 |
| [T05-统一数据元正式规划上下文与落标草稿门禁](T05-统一数据元正式规划上下文与落标草稿门禁.md) | P0 | IN_PROGRESS | F2-T02、F4-T02/T03/T04、T01 | 全局标准 owner、正式 planId、模型字段落标与安全返回链 |
| [T06-统一模型来源选择、系统编码与提交时实时复验](T06-统一模型来源选择系统编码与提交时实时复验.md) | P0 | IN_PROGRESS | F2-T03、F3-T03、F3-T05、F4-T03 | 业务名称选来源、系统码自动生成、resolver fail closed |
| [T07-统一 OpenMetadata 远端元数据部门可见性](T07-统一OpenMetadata远端元数据部门可见性.md) | P1 | READY | F4-T03、T06 | 远端表/FQN 归属映射、认证部门过滤与 fail closed |
| [T08-完成四层模型与API-Landing真实端到端验收](T08-完成四层模型与API-Landing真实端到端验收.md) | P0 | READY | F3-T08/T09/T10、T02/T03/T06 | 四层模型迁移、物化、API Landing 与真实 E2E |

## 完成标准

- [ ] 标准、指标、资产和运维保存稳定引用，不复制正文；指标 owner 不依赖 legacy `/metrics`。
- [x] 数据元、公共码表、度量单位、命名词典均有可访问 owner 页面、版本契约和权限证据。
- [x] ModelSpec revision 可追踪到 artifact、测试、发布、资产、指标和运行。
- [x] BUSINESS_FIRST/ASSET_FIRST 均不经过业务对象完成真实模型发布链路。
- [x] 旧深链、未分类记录、权限、失败恢复和 Chrome 95 通过。
- [x] Go/No-Go 明确区分产品退役、写冻结和物理删除状态。
- [ ] 全局数据元/业务分类不依赖浏览器规划草稿，模型字段落标只消费 canonical ModelSpec/WarehousePlan 上下文。
- [ ] 模型来源只能从当前计划已确认且可解析的来源中选择，系统 ID/版本和维度/层级编码不由用户手工构造。
- [ ] 提交时后端以当前计划事实重新解析来源，跨计划、未同步、未确认、不可用和版本漂移均 fail closed。
- [ ] OpenMetadata 远端列表和详情与本地正式目录使用同一认证部门和精确归属规则，缺少映射时 fail closed。
- [ ] 业务维度、逻辑模型、数据实现和物理资产四层在真实 API/PostgreSQL/Chrome95 中闭环。
- [ ] API 通过真实采集任务和 Landing 资产进入模型实现，mock 不作为 Sprint DONE 证据。
- [ ] 指标工作台可完成原子/派生指标编辑、依赖预检、校验、发布、版本、归档和精确版本回写。
