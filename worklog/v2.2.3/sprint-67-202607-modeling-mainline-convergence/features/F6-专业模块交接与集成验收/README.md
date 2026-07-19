# F6：专业模块交接与集成验收

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：F2-F5
**目标**：证明简化主线不是孤立页面演示，而是能够把模型交给标准、SQL/dbt、质量、发布、指标、资产和运维，并提供可回滚交付证据。

## Task

| Task | 优先级 | 状态 | 依赖 | 输出 |
|---|---|---|---|---|
| [T01-接通标准与指标专业模块](T01-接通标准与指标专业模块.md) | P0 | DONE | F3-T05、F5-T04 | 四类标准 owner + standards/metrics reference handoff |
| [T02-接通构建发布运行与血缘证据](T02-接通构建发布运行与血缘证据.md) | P0 | DONE | F3-T05、F4、F5-T03 | artifact/release/run/lineage loop |
| [T03-完成双起点与兼容Chrome95验收](T03-完成双起点与兼容Chrome95验收.md) | P0 | READY | T01/T02、F4-T04 | E2E/Chrome95 证据 |
| [T04-完成发布回滚与最终评审](T04-完成发布回滚与最终评审.md) | P0 | READY | T03、F5-T04 | Go/No-Go 与退出清单 |

## 完成标准

- [x] 标准、指标、资产和运维保存稳定引用，不复制正文。
- [x] 数据元、公共码表、度量单位、命名词典均有可访问 owner 页面、版本契约和权限证据。
- [x] ModelSpec revision 可追踪到 artifact、测试、发布、资产、指标和运行。
- [ ] BUSINESS_FIRST/ASSET_FIRST 均不经过业务对象完成闭环。
- [ ] 旧深链、未分类记录、权限、失败恢复和 Chrome 95 通过。
- [ ] Go/No-Go 明确区分产品退役、写冻结和物理删除状态。
