# T02：创建普通与 DBT_BACKED implementation

**优先级**: P0  
**状态**: IN_PROGRESS  
**依赖**: T01

## 目标

根据转换结论建立正确的实现所有权和输入版本，不把复杂 SQL 伪装成普通字段映射。

## 技术设计

- DESIGNER_GENERATED 复用普通 input mode、field mapping、cast/join/dedup 契约。
- DBT_BACKED 创建或 claim `DBT_MANAGED` implementation。
- 固定 sourceBinding/version 或 upstream ModelSpec/implementation 六元 pin。
- projectKey、dbtUniqueId 和技术编码服务端生成或验证唯一归属。

## 影响范围

- `ModelLifecycleService`、ModelImplementation repository/service。
- import candidate-to-implementation adapter。

## 验证

- [ ] 简单模型和复杂模型进入不同 ownership。
- [ ] summary/application 不直接绑定物理来源。
- [ ] source/upstream 版本漂移阻断 apply。

## 完成标准

- [ ] implementation revision/checksum 可由物理资产和 Sprint-69 候选消费。
- [ ] 所有系统编码无需人工输入。
