# T01: Admin Gateway基础层

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

为 `dts-platform` 建立统一的 admin upstream transport 和 support 组件。

## 技术设计

- 在 `service/admin/gateway/support` 下建立公共 transport
- 统一处理 `DtsAdminProperties`、URI 拼装、headers、service token、forwarded headers
- 统一 `ApiEnvelope` 解包与错误转换

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support/**`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/DtsAdminProperties.java`
- 对应单测

## 验证

- [ ] `cd source/dts-platform && ./mvnw -q -Dtest=AdminGatewayTransportTest test`

## 完成标准

- [ ] 可被各 domain gateway 复用
- [ ] 有 transport 层失败与成功路径测试
