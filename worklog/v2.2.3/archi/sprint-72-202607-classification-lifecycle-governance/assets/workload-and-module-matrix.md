# Sprint-72 工作量与模块矩阵

## 工作量

| 模块/工作流 | 主要改动 | 估算人日 | 风险 |
|-------------|----------|----------|------|
| dts-common | 四级比较、max、未知值 fail-closed 契约 | 2～3 | CRITICAL 共享影响 |
| dts-platform 密级事实 | 快照、事件、解析、CAS、兼容投影、迁移 | 8～10 | CRITICAL |
| JDBC/API/文件接入 | 声明采集、字段映射、seal、ODS 准入 | 11～14 | HIGH |
| 血缘与建模发布 | OpenLineage/dbt/字段血缘传播、重算、发布门禁 | 12～15 | HIGH |
| 生命周期/销毁 | 六阶段 projection、回收站、恢复、永久销毁适配器和证明 | 13～17 | HIGH / 不可逆 |
| 指标/报表/API/数据产品 | 最高密级派生、查询分享导出门禁、缓存失效 | 8～11 | HIGH |
| dts-analytics 大屏 | 引用解析、服务端计算、旧手工降密退役、公开链接收敛 | 10～13 | HIGH |
| dts-platform-webapp | 接入向导、台账、生命周期工作台、大屏解释 UI | 8～10 | MEDIUM |
| 迁移与真实验收 | dry-run、对账、迁移、权限矩阵、Chrome 95、运行证据 | 10～13 | HIGH |

部分工作在 Feature 间复用，去重后的 Sprint 总估算为 **80～103 人日**。

## 影响模块

| 模块 | 现状 | Sprint-72 责任 |
|------|------|----------------|
| `source/dts-common` | `SecurityLevelCatalog` 已有四级枚举和默认值 | 固化比较/max/fail-closed 契约，避免各服务自定义 rank |
| `source/dts-platform` catalog | dataset/table 有可编辑 classification，column 只有 sensitiveTags | 建立统一事实、字段密级、兼容投影和解释 API |
| `source/dts-platform` infra/etl | JDBC、ODS、Excel/CSV 多处默认 INTERNAL | 首次落盘前 seal，缺 seal 阻断生产写入 |
| `source/dts-ingestion` | 未携带业务密级传播契约 | 接收 seal/version/checksum，运行和重试保持一致 |
| `source/dts-platform` lineage/modeling | 已有 dataset/column lineage、OpenLineage、dbt 同步 | 血缘写入后传播，发布绑定密级快照 |
| `source/dts-metrics` | 查询和 artifact 层已消费部分 asset classification | 指标/派生指标取全部引用最高密级并回写事实 |
| `source/dts-platform` services | API、数据产品、BI report link 各自可写 classification | 改为下游派生 + 人工下限，只升不降 |
| `source/dts-analytics` | 大屏创建手工选密，可有原因降密 | 解析全部展示来源，服务端自动取最高值，退役降密 |
| `source/dts-platform-webapp` | 已有密级 Tag、选择器、资产台账和生命周期零散入口 | 展示来源/有效密级、接入封存、工作台、监控与阻断原因 |
| `source/dts-admin` | 人员密级、审计目录、工作流配置 | 复用人员密级和审计；按需配置审批模板，不改四级语义 |
| PostgreSQL/Liquibase | 生命周期申请和各业务字段已存在 | 新增事实/事件/回收站/销毁证明与索引约束 |

## 批次建议

| 批次 | 范围 | 退出条件 |
|------|------|----------|
| Batch A | F1 + F2 接入事实 | 新接入数据首次落盘前有 seal；旧链路不回归 |
| Batch B | F3 + F5 血缘和消费 | 模型、指标、API、报表可解释且只升不降 |
| Batch C | F6 大屏 | 全部数据源类型能解析；发布和运行时都取最高密级 |
| Batch D | F4 + F7 生命周期 | 审批、归档、回收站、恢复、永久销毁证明和监控闭环 |
| Batch E | F8 存量迁移 | dry-run 无候选降级，双读对账通过，真实环境 Go/No-Go |

## 关键风险

1. 旧 `classification` 写入口数量大，必须先列全并逐个冻结，不能只改 UI。
2. `SecurityLevelCatalog` 和 `CatalogDataset` 为 CRITICAL 影响面，优先新增服务/表，不直接改变既有 getter/setter 语义。
3. 字段血缘可能不完整；不能因为缺血缘默认为低密级。
4. 大屏存在任意 SQL/API 和多级下钻，发布前必须解析完整引用。
5. 上游升密会使既有共享、公开链接和导出资格失效，需要明确用户通知和审计。
6. 永久销毁不可逆，只允许显式适配器、双人复核、幂等执行和销毁证明。
7. 存量数据质量未知，迁移必须 dry-run 和分批，不允许全表一次性静默回填。
