# 产品与架构决策登记表

**用途**：这是 Sprint-83 从 DRAFT 进入 READY 的硬门。每项必须由讨论确认后标记 `ACCEPTED` 或给出替代方案；未确认不得编码。

| ID | 决策问题 | 架构建议 | 当前状态 | 影响 Feature |
|---|---|---|---|---|
| D01 | “可视化后的表”包含什么 | P0 包含逻辑表/字段、dbt 依赖图、物理表结构与样例数据；不含任意 SQL 的可编辑转换画布 | PROPOSED | F1/F2 |
| D02 | DBT_MANAGED 的编辑权 | SQL/Jinja 拥有技术结构；可视化技术结构只读；ModelSpec 业务语义可编辑 | PROPOSED | F1/F2/F3 |
| D03 | 高级建模的信息架构 | 作为现有模型工作台高级模式，保留深链；不新增菜单和第二个模型列表 | PROPOSED | F2 |
| D04 | 外部接入方式 | P0 只支持 ZIP 快照；Git clone/sync/push 延后 | PROPOSED | F3/F5 |
| D05 | 重新导入冲突 | checksum 相同 SKIP；单边变化显式 UPDATE；双边变化 CONFLICT，禁止自动覆盖 | PROPOSED | F1/F3 |
| D06 | 导出范围 | Sprint-83 明确不做 ZIP 导出；未来如纳入，另建 Feature，只允许 revision-pinned ZIP，不做 Git push | PROPOSED_DEFERRED | 非目标 |
| D07 | 部分成功与撤销 | 允许部分成功并展示逐项结果；retry 幂等；撤销使用前向修订，不删历史 | PROPOSED | F3/F5 |
| D08 | Catalog 登记时点 | DRAFT 仅在建模域可见；发布/物化后才登记为可消费 Catalog 资产 | PROPOSED | F4 |
| D09 | 首期 dbt 版本/adapter | 由真实样本画像确定；不得凭代码默认值宣称兼容 | OPEN | F0/F1/F6 |
| D10 | source-only 包处理 | 静态解析可得结构则 STRUCTURE_VIEW_ONLY；缺业务语义/动态依赖则 BLOCKED；P0 不在 inspect 中执行 dbt | PROPOSED | F1/F3/F5 |
| D11 | 所有权转换 | Sprint-83 不实现所有权转换；导入模型保持 DBT_MANAGED。未来另立 Feature，映射现有 maintainer/admin + write，不发明未落地权限 | PROPOSED_DEFERRED | 非目标 |
| D12 | 数据预览上限和脱敏 | 最大 500 行沿用现有上限；必须绑定 candidate/relation evidence，并应用列权限、密级和脱敏 | PROPOSED | F2/F5 |

## 下一轮讨论建议顺序

1. 先确认 D01、D02、D03，冻结产品形态。
2. 再确认 D04、D05、D07，冻结导入生命周期。
3. 提供脱敏样本后确认 D09、D10。
4. 最后确认 D06/D11 延后，以及 D08、D12 的交付边界。
