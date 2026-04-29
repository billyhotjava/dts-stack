# T03: Service/Pipeline/Trigger 结果模型

**优先级**: P0  
**状态**: READY  
**依赖**: T01, T02

## 目标

让 OpenMetadata 写路径调用方能拿到成功、跳过、失败和失败原因，而不是只看日志。

## 范围

- 定义 ensure service、ensure pipeline、trigger pipeline 的结果对象。
- 区分 disabled、unsupported、invalid config、remote error、success。
- 将结果挂接到接入任务执行日志或审计字段。
- 保持现有接口兼容，必要时新增方法而不是破坏调用方。

## 完成标准

- [ ] 调用方能判断本次是否实际请求 OpenMetadata。
- [ ] OpenMetadata 4xx/5xx 有状态码和安全摘要。
- [ ] disabled/unsupported 不被误报为成功。
- [ ] 结果可用于后续 UI 或日志展示。
