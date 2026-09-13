# T05: Mock API fixtures 与契约测试

**优先级**: P1
**状态**: DRAFT
**依赖**: T02, T03

## 目标

建立可重复的 mock API 测试样本，避免后续接入能力只能依赖客户现场接口验证。

## 范围

- 准备 JSON array、JSON object、nested records、pagination、auth fail、rate limit、schema drift 样本。
- 提供 mock server 或 WireMock 类测试配置。
- 覆盖 testConnection、preview、schema inference、error classification。

## 完成标准

- [ ] 本地和 CI 能稳定运行 mock API 测试。
- [ ] 至少覆盖 8 类典型响应。
- [ ] 鉴权、分页、限流、解析失败均有 fixture。
- [ ] fixture 文档说明输入、预期输出和错误分类。

