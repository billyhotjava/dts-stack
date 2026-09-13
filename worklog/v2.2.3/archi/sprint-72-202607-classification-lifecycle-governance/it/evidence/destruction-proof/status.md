# 销毁安全证据状态

本轮未对用户业务资产执行临时销毁、恢复或永久销毁；所有破坏性验证均限定在
Testcontainers 创建的临时 PostgreSQL 和独立托管副本中。

已验证：

- 临时销毁后资产进入回收站，托管副本不可继续消费。
- 保留期内恢复后托管副本和生命周期状态恢复。
- 申请人不能作为第二审批人绕过双人控制。
- 两名不同审批人批准后，永久销毁删除 DTS 托管副本。
- 外部源表仍存在，DTS 不执行反向 DROP。
- 销毁证明、审批事实和不可变审计事件在业务副本删除后仍可查询。

对应隔离集成测试：

- `CatalogLifecycleDestructionIT#requesterAndSameSecondApproverCannotCollapseDualControl`
- `CatalogLifecycleDestructionIT#temporaryRestoreAndDualControlPermanentDestructionStayInsideDtsBoundary`

结论：隔离销毁安全证据 `PASS`；未触碰用户业务数据。
