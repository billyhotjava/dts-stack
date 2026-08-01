# T02：建立真实 dbt 包兼容画像与 golden fixtures

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01

## 目标

用获授权的脱敏包实测客户 dbt Core、manifest、adapter、资源类型、复杂度和脏数据，而不是凭源码上限推断兼容性。

## Contract-first

- **输入**：FX-01～05；每个 ZIP 必须记录来源授权、脱敏方式和 SHA-256。
- **输出**：fixture inventory：版本、adapter、文件/模型/source/test/macro 数、节点/边/深度、动态表达式比例、缺失字段率、解析耗时。
- **失败路径**：含凭据/敏感数据的样本拒收；无法脱敏则由客户在受控环境运行画像脚本，只归档统计与 checksum。
- **数据边界**：样本不进入 Git；测试夹具必须是重新构造的最小无敏感版本。

## 验证

- [ ] FX-01～05 全部有实际命令和输出。
- [ ] 将兼容版本、NFR 秒数和阻断规则回写 domain-profile/nfr-budget。

## Definition of Done

- [ ] D09 可由实测数据确认。
- [ ] 后续 Task 不需要再次扫描客户包来决定基本范围。
