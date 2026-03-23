# T03: 缩减 AddaxJobService 职责边界

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标
为超大 Addax 服务建立更明确的拆分方向，逐步下沉模板生成、参数归一和作业校验逻辑。

## 技术设计

- 识别纯转换逻辑与外部交互逻辑
- 优先把易测的纯函数/归一化逻辑下沉
- 保持 Addax 作业输出契约不变

## 影响范围

- [AddaxJobService.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java)
- [AddaxJdbcConfigNormalizer.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJdbcConfigNormalizer.java)
- 相关 DTO / helper / 测试

## 当前进展

- 已新增 JDBC 配置归一化 helper：
  - [AddaxJdbcConfigNormalizer.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJdbcConfigNormalizer.java)
- 已把以下逻辑从 [AddaxJobService.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java) 中下沉：
  - JDBC URL 归一化
  - writer connection 归一化
  - driver 推导与补齐
  - host/port/database 到 JDBC URL 的拼装
- Addax 作业生成主链保持原有输出契约

## 验证

- [x] Addax 相关测试通过
- [x] 生成作业关键路径回归通过

已验证：

- [x] `cd source/dts-ingestion && mvn -Dtest=AddaxJdbcConfigNormalizerTest,AddaxJobServiceTest test`
- [x] `cd source/dts-ingestion && mvn -Dtest=IngestionExecutionQueryServiceTest,IngestionTaskQueryServiceTest,IngestionTaskResourceTest,AddaxJdbcConfigNormalizerTest,AddaxJobServiceTest,IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,IngestionTaskFullRefreshExecutionTest test`

## 完成标准

- [x] AddaxJobService 体量下降
- [x] 关键转换逻辑独立可测
