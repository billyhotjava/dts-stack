# Feature 组合验证状态

已通过：

- 密级枚举未知值 fail-closed、只升不降策略和迁移候选降级阻断。
- 传播任务 RETRY 持久化、缺血缘进入 BLOCKED 并保存证据。
- 接入 seal/version/checksum guard 及缺密级、未知编码、校验和不一致阻断。
- JDBC 元数据字段密级接入、字段最高密级提升资产密级，并在真实 PostgreSQL Testcontainers 中通过。
- Excel/CSV 文件密级下限、字段声明和 ODS 封存定向测试通过。
- dbt manifest、OpenLineage 接收和 Airflow DAG lineage/classification 参数定向测试通过。
- 指标生命周期密级一致性。
- 大屏创建/更新不可降级、访问权限、有效密级取全部展示来源最高值。
- 旧公开链接、旧分享、导出、缓存及管理员旁路均经过服务端自动化权限回归。
- 临时销毁、恢复、双人永久销毁、销毁证明留存和外部源表不反向 DROP 已在隔离 PostgreSQL 中通过。
- 存量字段从 dataset/table 继承密级的迁移场景已在隔离 PostgreSQL 中通过。
- 资产台账、密级事实、生命周期工作台、监控、迁移 dry-run 和大屏列表真实页面。

尚未形成生产外部系统组合证据：

- 部署最终源码后的 dbt target、OpenLineage/Airflow 和 Excel/CSV 真实运行链。
- INTERNAL/SECRET/CONFIDENTIAL 三类真实人员账号的端到端越级访问演练。
- 部署最终源码后的存量 dry-run、apply 和冻结旧写入口。
- 大屏编辑、发布、公开访问的完整 Chrome 95 用户旅程。

结论：编码与自动化组合验证 `PASS`；生产外部闭环 `PARTIAL`。
