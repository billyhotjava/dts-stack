# F5 迁移核对

- 批次：`legacy-objects-608ba1f186f04890`
- 校验和：`608ba1f186f0489041e6db22b49c73f6c0170a8383802657383e2a9bee540d24`
- 执行结果：`COMPLETED_ACCOUNTED`
- 处置守恒：源对象 5 = 隔离待分类 4 + 只读归档 1；未入账 0。
- 引用守恒：4 models、9 mappings、4 dimensions、5 metrics、13 artifacts、2 runs、2 reviews 均被逐对象计数；孤儿计数全部为 0。
- 无 catalog domain 显式匹配的 4 条记录均进入 `NEEDS_CLASSIFICATION`，未按名称猜测或创建目标。
- 相同批次再次执行返回 `replayed=true`，目标计数和校验和不变。

结论：当前没有可自动批准迁移的记录，所有旧记录均处于可审计的隔离或只读归档状态。
