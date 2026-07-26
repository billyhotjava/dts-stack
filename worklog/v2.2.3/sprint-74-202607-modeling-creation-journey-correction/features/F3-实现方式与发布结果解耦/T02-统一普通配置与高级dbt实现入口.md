# T02：统一普通配置与高级 dbt 实现入口

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F3/T01

## 目标

让用户在数据实现阶段显式选择普通配置或高级 dbt，并始终绑定同一逻辑模型和实现 revision。

## 技术设计（Contract-first）

- **输入契约**：ownership=`DESIGNER_GENERATED | DBT_MANAGED`；当前 ModelSpec CAS。
- **输出契约**：唯一 ModelImplementation owner；切换创建新 implementation revision。
- **普通→dbt**：从当前 implementation 生成初始 dbt artifact，锁定 modelSpecId/revision/checksum。
- **dbt→普通**：禁止静默降级；必须显式放弃自定义并调用受控转换 API。
- **错误路径**：已有自定义 dbt 不可被表单自动覆盖；implementation revision 漂移锁定入口；转换冲突返回 409。
- **复用点**：Sprint-67 普通/高级 STG 规则、Sprint-70 dbt parser/artifact；账本 L12/L13。
- **实现方案**：在 ImplementationStage 顶部增加 ownership 选择和说明；复用现有 claim/convert API，不在 physical stage 导航 dbt。

## UI 交互规格

- 普通配置说明“适合标准映射和装载，系统管理 ephemeral STG”；
- 高级 dbt 说明“适合复杂 SQL、显式 STG 和自定义物化”；
- 切换前二次确认影响；成功后仍停留数据实现并显示 revision。

## 影响范围

ImplementationStage、dbt workbench navigation、claim/convert APIs、source-contract tests。

## 验证（RED→GREEN）

- [ ] 两种 ownership 绑定同一 ModelSpec
- [ ] 普通转 dbt 不创建第二个逻辑模型
- [ ] dbt 自定义不会被普通表单覆盖
- [ ] 旧 implementation revision 无法进入高级工作台

## Definition of Done

- [ ] 架构：dbt 只是实现 owner
- [ ] UI：入口、切换和风险说明清楚
- [ ] 切片：IT-06/IT-07 通过

