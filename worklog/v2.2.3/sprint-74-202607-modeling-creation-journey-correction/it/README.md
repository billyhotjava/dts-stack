# Sprint-74 集成验收计划

**状态**：PENDING；本文件定义验收，不包含占位成功声明。

| ID | Journey | 通过条件 | 证据文件 |
|---|---|---|---|
| IT-01 | 新建入口无默认类型 | 打开抽屉时未选任何类型，保存按钮不可完成 | `it-01-create-no-default/` |
| IT-02 | 四类业务目的决策 | 四张卡均显示“适用/不适用/例子”，选择后提交正确 modelType；服务端按经典规则返回 layer，页面分别展示二者 | `it-02-type-decision/` |
| IT-03 | 维度逻辑设计 | 无来源、无实现保存 DIMENSION 并达到 DESIGNED | `it-03-dimension-designed/` |
| IT-04 | 事实逻辑设计 | 业务过程→粒度→TIME 字段→维度引用闭合，错误时间引用被就地提示 | `it-04-fact-designed/` |
| IT-05 | 当前阶段 blocker | DRAFT 只显示 DESIGNED 缺口；展开后未来项标“以后处理”且不计数 | `it-05-current-gate/` |
| IT-06 | 普通数据实现 | DESIGNED 模型选择规划物理来源、映射、物理名和装载策略并验证 | `it-06-designer-implementation/` |
| IT-07 | 高级 dbt 实现 | 从数据实现选择高级 dbt，锁定同一 modelSpec/revision | `it-07-dbt-implementation/` |
| IT-08 | 发布结果 | 未发布显示空态；发布后显示真实资产/DDL/测试/血缘，无 dbt 编辑入口 | `it-08-published-result/` |
| IT-09 | 财务项目纠错 | r4 preview 为 eligible；确认字段编码和业务维度后产生新 DIMENSION revision，r4 可读 | `it-09-finance-reclassify/` |
| IT-10 | 治理阶段 | 标准/质量/密级不阻断 DESIGNED；按计划策略在 RELEASE_READY 精确阻断 | `it-10-governance-stage/` |
| IT-11 | 兼容迁移 | 旧中文字段、sourceRefs/dependsOn、implementationPolicy 快照可读且迁移可回滚 | `it-11-compatibility/` |
| IT-12 | 四态与兼容 | 创建/逻辑/实现/结果页空、加载、错误、成功四态；Chrome95 无控制台错误 | `it-12-ui-states/` |

## 最终验证命令类别

实施期在各 Task focused RED→GREEN 后，仅在 F1～F4 完成时集中执行：

1. dts-platform 定向/组合后端测试；
2. dts-platform-webapp Node source-contract；
3. `pnpm build`；
4. clean DB migration + rollback rehearsal；
5. 真实 Chrome95 + Spring Security + API + PostgreSQL + dbt E2E；
6. GitNexus `detect_changes` 范围审计。

任何一项缺失，Sprint 不得 DONE。
