# T05: dts-metrics 页面真实功能验收

**优先级**: P0
**状态**: READY
**依赖**: Sprint-32 F5

## 目标

确认 `dts-metrics` 独立服务中的指标资产列表、主题域映射、业务对象 Join、指标公式配置、DWS/ADS 生成、发布运行页均为真实可操作页面；`dts-platform-webapp` 只保留菜单链接和平台权限入口。

## 技术设计

验收页面：

1. `/metrics/center` 指标资产列表；
2. `/metrics/semantic/subjects` 主题域映射；
3. `/metrics/semantic/objects` 业务对象 Join；
4. `/metrics/semantic/metrics` 指标公式配置；
5. `/metrics/semantic/models` DWS/ADS 生成；
6. `/metrics/semantic/publish` 发布运行；
7. `/metrics/operations` 运行和告警。

每个页面必须至少有：

- list/read API；
- create/update 或 preview/publish/run 中的一个核心动作；
- 空态；
- 错误态；
- platform permission/capability 错误提示。

## 影响范围

- `source/dts-metrics`
- `source/dts-metrics-webapp` 或 dts-metrics 静态前端资源目录
- `source/dts-platform-webapp` 菜单链接仅做入口，不承载指标业务页面

## 验证

- [ ] dts-metrics 前端 build 通过。
- [ ] 至少 1 条 smoke test 覆盖 platform 菜单跳转到 `/metrics/**`。
- [ ] 至少 1 条页面级测试覆盖指标公式配置或模型生成。

## 完成标准

- [ ] 指标与语义中心不再是 demo 页面。
- [ ] platform 和 metrics 的边界符合“platform 管权限，metrics 做业务”的拆分原则。
