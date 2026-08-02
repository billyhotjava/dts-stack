# T02：收敛规范化 projection seam 与 parser 删除门禁

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01

## 目标

指定现有 `ModelPackage` converter/projection 为规范化 seam，先让 P0 artifact-rich 表示和导入消费者通过同一 projection 读取事实；同时产出保留/适配/删除矩阵。物理删除不属于本 Task DoD，统一交由 F5/T05 在 caller=0 后执行。

## Contract-first

- **输入**：账本 CL-09、CL-10、CL-15、CL-21～CL-24，`assets/dbt-compatibility-and-source-only-contract.md` 与 FX-01。
- **输出**：consumer → normalized field mapping；owner adapter 与 normalized projection 的责任边界；保留/适配/删除矩阵；artifact-rich 三轴版本兼容与 reason code。source-only enforced contract 和 macro 隐藏依赖扩展由 F3/T06 消费同一 seam，不在本 Task 重写 parser。
- **错误路径**：消费者需要 schema 未提供的字段时，先扩展同一 ModelPackage/owner projection；禁止复制正则、YAML parser 或 uniqueId 算法。尚有非建模消费者的 reader 只能登记为 owner adapter，不得为追求“单文件”提前删除。
- **安全**：source-only 解析不执行模型 SQL、不下载 packages、不读取 profiles secrets。
- **所有权**：conversion capability 与 Implementation ownership 解耦；外部 ZIP 即使属于安全 SQL 子集，apply 后也必须是 `DBT_MANAGED`。

## 验证

- [ ] FX-01 经表示、导入、诊断、血缘得到一致 uniqueId/依赖/字段/materialization。
- [ ] source-only/complex 所需字段被记录为同一 seam 的 P1 扩展点，没有创建第二 YAML/SQL parser。
- [ ] 安全 SQL 子集导入后仍为 `DBT_MANAGED`，不会被 classifier 静默改成 `DESIGNER_GENERATED`。
- [ ] 退役候选有消费者、迁移顺序、owner 和 caller=0 证据要求；实际删除由 F5/T05 验收。

## Definition of Done

- [ ] 所有 P0 建模消费者只依赖一个 normalized projection seam；非建模 reader 通过已登记 owner adapter 映射。
- [ ] 没有新增双写、feature flag 或隐藏兼容 parser；删除工作不阻断本 Task 完成。
