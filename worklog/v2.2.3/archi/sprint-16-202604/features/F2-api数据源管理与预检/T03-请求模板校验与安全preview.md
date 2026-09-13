# T03: 请求模板校验与安全 preview

**优先级**: P0
**状态**: DRAFT
**依赖**: T01, T02

## 目标

在创建任务前对 endpoint/resource 请求模板做安全 preview，验证请求参数、响应结构和 record path。

## 范围

- 校验 method、path、query、body template、headers override。
- 支持变量占位符的默认值和测试值。
- 限制 preview 响应大小、超时、重定向和下载类型。
- 输出 preview sample、record count、record path 命中情况和解析错误。

## 完成标准

- [ ] Preview 不能访问内网危险地址或文件协议。
- [ ] 超大响应被截断并有明确提示。
- [ ] record path 无命中时返回可操作诊断。
- [ ] Preview 结果不持久化敏感字段明文。

