# 非功能预算 (Gate G1)

**依据**: `assets/domain-profile.md` + DTS 领域不变量  
**适用范围**: DataMart、DimensionDefinition、DIMENSION ModelSpec、计划业务范围和发布资产交接

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 设计状态 |
|------|------|---------------------|-----------|----------|
| 分页 | DataMart 列表默认 10，最大 100 | API 契约测试断言 `size=101` 返回 400；UI source-contract 断言默认 10 | F2/T01、T02 | PASS |
| 关联上限 | 单 DataMart 最多 100 个 domain；单维度最多 200 个属性；单次导入最多 500 字段 | 边界测试分别断言 101/201/501 返回 422 | F2/T01、F3/T01、F4/T02 | PASS |
| 查询效率 | 列表/usageCount 单页禁止 N+1；单请求数据库查询 ≤5 | Hibernate statistics IT 断言查询数 ≤5 | F2/T01、F3/T01 | PASS |
| 索引 | DataMart code、状态/名称、mart-domain、plan-mart、dimension scope、model active uniqueness 均有显式索引 | Liquibase IT 查询 `pg_indexes` | F2/T01、F3/T01、F4/T01 | PASS |
| 并发 | POST 幂等；PUT CAS；同 scope 重复维度表并发只成功一次 | 并发 IT 断言一条成功、一条 409/412，数据库无重复活动行 | F2/T01、F3/T01、F4/T01 | PASS |
| 延迟 | 当前量级及 10k DataMart/50k relation 基准下列表 P95 ≤800ms | Testcontainers 装载量级夹具 + 基准脚本断言 | F2/T03 | PASS |
| 超时 | 人员目录/元数据/资产注册连接 ≤5s、读取 ≤10s；幂等重试最多 2 次 | 配置/桩测试断言 timeout 非空、重试只用于幂等调用 | F2/T03、F5/T01 | PASS |
| 审计 | DataMart 5 类写动作和计划绑定必须有分类审计记录 | IT 查询审计记录且 action 非“未分类” | F2/T03 | PASS |
| 权限 | 越权返回 403；不可见 domain/mart 返回 404，避免枚举 | Spring Security IT | F2/T03、F3/T03 | PASS |
| 租户 | 跨租户 code 可重复，跨租户 ID 不可读写 | Repository/REST IT | F2/T03、F3/T03 | PASS |
| 迁移 | expand/backfill 可 dry-run；未解析存量不自动猜测；无破坏性 rollback | Liquibase clean DB + 存量夹具 + dry-run 结果断言 | F0/T02、F3/T03 | PASS |
| 兼容性 | Chrome 95；现有 DOMAIN 维度和无 mart ModelSpec 可正常读取 | legacy production build + Chrome95 smoke + 旧契约回归 | F3/T03、F5/T02 | PASS |
| 可恢复 | 资产注册失败为 PARTIAL，可按相同幂等键重试，不回滚已发布模型 | 故障注入 IT | F5/T01 | PASS |

## DoD 回跑约定

所有预算项在 F5/T02 统一回跑。这里的 PASS 表示预算、阈值、检查方式和归属均已钉死，不表示实现已经通过。
