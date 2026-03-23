# PLT-002：产出表重建契约收口

## 目标

统一 `DbtOutputRelationService` 与 `EtlResource` 的重建语义，并同步更新测试基线。

## 主要文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

## 交付标准

- `prepareRebuild()` 语义明确
- controller response 字段含义明确
- 相关测试全部改为验证新契约
