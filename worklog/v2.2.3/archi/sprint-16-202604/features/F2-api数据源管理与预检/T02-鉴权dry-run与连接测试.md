# T02: 鉴权 dry-run 与连接测试

**优先级**: P0
**状态**: DRAFT
**依赖**: T01, F1/T02

## 目标

提供统一的 API 连接测试能力，验证 base URL、鉴权 provider、TLS、网络可达性和基础响应状态。

## 范围

- 新增 `testConnection` API，不落库响应内容。
- 支持 provider 注入后的 dry-run。
- 返回 HTTP 状态、耗时、响应头白名单、错误分类和脱敏诊断。
- 对响应体只允许截断样本，并默认不保存。

## 完成标准

- [ ] 鉴权失败归类为 AUTH。
- [ ] DNS/TLS/timeout/4xx/5xx 有明确分类。
- [ ] dry-run 不泄露 Authorization、Cookie、token。
- [ ] 测试结果可用于前端诊断提示。

