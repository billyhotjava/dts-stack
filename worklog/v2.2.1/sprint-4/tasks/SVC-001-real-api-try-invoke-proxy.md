# SVC-001: ApiCatalogService 引入真实 tryInvoke 代理执行链路

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/services/ApiCatalogService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/services/`
- 相关 test

## 目标

- 将当前基于 response schema 生成样例值的 `tryInvoke` 改成真实代理调用

## 交付

- `ApiInvokeProxyService` 或等价代理能力
- safe timeout / request mapping / response capture
- policy hit / masked field 回显

## 验收

- `tryInvoke` 返回真实下游响应，不再固定 `sample_*`
- 失败时能看到超时、认证失败、下游错误等原因
- 不破坏现有 API 元数据中心能力

## 当前进度

- 状态：TODO
- 备注：一期只支持最常见 HTTP/JSON 调用场景
