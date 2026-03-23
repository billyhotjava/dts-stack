# PLT-001：Airflow DAG ready 错误传播收口

## 目标

让 DAG ready 检查能够区分：

- DAG 不存在
- Airflow 服务错误
- Airflow 鉴权错误
- 网络异常

## 主要文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

## 交付标准

- 不再把真实 HTTP 错误伪装成“DAG 未注册”
- `EtlResourceTest` 覆盖对应分支并通过
