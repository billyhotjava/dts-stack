# T04: OpenAPI 与旧接口兼容测试

**优先级**: P0
**状态**: READY
**依赖**: T01,T02,T03

## 目标

用契约测试证明新 API 可供前端使用，旧语义 API 不发生破坏性变化。

## 技术设计

- 新增 API response fixture 和错误码清单。
- 为旧 `/api/semantic/business-objects`、`/api/semantic/models` 保留回归用例。
- 对分页、权限、revision 冲突、空数据和后端异常分别断言。

## 影响范围

- `source/dts-platform/src/test/**`
- `source/dts-platform-webapp/src/api/**.source-contract.test.ts`
- API 文档和 `it/evidence/api/`。

## 验证

- [ ] API 测试先 RED 后 GREEN。
- [ ] 旧接口响应字段和状态码无净减少。

## 完成标准

- [ ] 新旧接口契约均有可重复执行的命令和证据。
