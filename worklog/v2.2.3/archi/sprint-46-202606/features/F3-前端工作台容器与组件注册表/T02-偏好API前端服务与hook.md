# T02: 偏好 API 前端服务与 hook

**优先级**: P0  
**状态**: DONE  
**依赖**: F2-T02

## 目标

封装前端工作台偏好 API，让首页和自定义抽屉共享同一份加载、保存、重置和错误处理逻辑。

## 技术设计

新增或改造前端服务：

- `fetchWorkbenchPreferences()`: 调用 `GET /api/workbench/preferences`。
- `saveWorkbenchPreferences(items)`: 调用 `PUT /api/workbench/preferences`。
- `resetWorkbenchPreferences()`: 调用 `POST /api/workbench/preferences/reset`。
- `useWorkbenchPreferences()`: 暴露 `availableComponents`、`items`、`loading`、`saving`、`error`、`reload`、`save`、`reset`。

状态规则：

- loading: 首页展示骨架或加载块，按钮禁用。
- error: 展示错误态和“刷新”按钮，不展示假数据。
- empty: 无个人配置时使用后端默认模板。
- cache: localStorage 只可作为短期读取缓存，服务端返回后必须覆盖。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/**`
- `source/dts-platform-webapp/src/lib/request/**`
- `source/dts-platform-webapp/src/routes/**`

## 验证

- [x] RED: 单测或契约测试断言 GET/PUT/reset 参数和错误态，确认失败。
- [x] GREEN: hook 实现后测试通过。
- [x] 模拟 500 时首页不出现 demo 数字。

## 完成标准

- [x] 首页和抽屉不直接拼接口路径。
- [x] 保存成功后内存状态与服务端返回一致。
- [x] 保存失败时保留草稿并给出错误提示。
