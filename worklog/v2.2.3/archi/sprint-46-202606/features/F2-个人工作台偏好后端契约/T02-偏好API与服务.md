# T02: 偏好 API 与服务

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

提供当前登录用户工作台偏好的 GET、PUT、reset 接口。

## 技术设计

新增平台侧接口：

- `GET /api/workbench/preferences`
- `PUT /api/workbench/preferences`
- `POST /api/workbench/preferences/reset`

服务层负责：

- 从安全上下文解析当前 username。
- 无配置时返回角色默认模板。
- 保存时标准化 order。
- reset 删除个人配置或覆盖为默认模板。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/**`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/**`
- `source/dts-platform/src/test/java/**`

## 验证

- [x] RED: Resource/Service 测试断言 API 返回默认模板并可保存，确认失败。
- [x] GREEN: API 和 service 实现后测试通过。
- [x] 保存后再次 GET 返回相同顺序。

## 完成标准

- [x] 三个 API 可用。
- [x] 请求体不能指定其他 username。
