# T01: 前端、API、后端单元与集成 RED-GREEN

**优先级**: P0
**状态**: READY
**依赖**: F2,F3,F4,F5

## 目标

为新契约、页面状态、API 校验、PostgreSQL 持久化和 dbt 编译建立 TDD 测试基线。

## 技术设计

- 前端：source-contract、纯函数、组件状态和 API client tests。
- API：Resource contract、错误码、权限、revision 冲突和幂等测试。
- 后端：Service 单元、PostgreSQL migration/repository integration、compiler tests。
- dbt：fixture `dbt parse`、schema/test generation tests。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/**.test.ts`
- `source/dts-platform/src/test/**`
- `it/evidence/{frontend,api,backend,dbt}/`

## 验证

- [ ] 新测试先执行并得到预期 RED。
- [ ] 最小实现后同一命令 GREEN。
- [ ] 覆盖率目标不低于 80%，并记录未覆盖原因。

## 完成标准

- [ ] 没有只写测试但未执行的伪 TDD 证据。
