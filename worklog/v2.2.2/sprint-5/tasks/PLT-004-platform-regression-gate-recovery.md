# PLT-004：Platform 最小回归门禁恢复

## 目标

把当前 review 涉及的最小后端回归面恢复为绿色，并作为后续重构门禁。

## 主要文件

- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtDagServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

## 交付标准

- 目标测试组全部通过
- 文案、等待时间和错误分类断言与实现一致
- 后续相关改动默认先跑这组测试
