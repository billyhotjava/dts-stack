# T04：实现来源/实施漂移的 FAILED_STALE 门禁

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T02～T03

## 目标

运行期间来源 epoch、model revision、implementation revision 或 artifact checksum 变化时，旧成功回执不能发布新上下文。

## Contract-first

- **输入**：运行起点 pins 与 callback 时 current state。
- **输出**：一致则 SUCCEEDED/BUILT；任一 pin 漂移则 FAILED_STALE + drift refs。
- **错误路径**：旧 worker/callback、重复回调、乱序回调、来源恢复后旧 epoch 均不得覆盖新 attempt。
- **恢复**：用户基于 current revision 创建新 candidate/attempt；不复用旧物理结果。

## 验证

- [ ] 执行中改 SQL、改 ModelSpec、回退/恢复来源、旧 callback 四类并发 IT。

## Definition of Done

- [ ] stale 结果不能登记 Catalog、展示为成功或提供数据预览。
