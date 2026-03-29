# Sprint-26: Platform统一Admin Gateway

**时间**: 2026-03
**状态**: IN_PROGRESS
**目标**: 将 `dts-platform` 与 `dts-platform-webapp` 对 `dts-admin` 的调用统一收口到 `dts-platform` 后端 gateway 机制中

## 背景

当前平台侧已经存在多套直连 `dts-admin` 的实现，导致：

- 前端和后端都暴露了对 admin 拓扑的依赖
- headers、token、timeout、错误处理重复实现
- 新增跨系统调用时缺乏统一接入标准

本轮目标不是只修“部门目录”，而是把现有平台侧 admin 调用全量收口，并以目录域作为第一批样板。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | Platform统一Admin Gateway | 5 | IN_PROGRESS |

## 完成标准

- [ ] `dts-platform` 建立统一 admin gateway 基础层
- [ ] 当前所有后端 admin 直连调用迁入 gateway
- [ ] `dts-platform-webapp` 不再直接读取 `adminApiBaseUrl`
- [ ] 数据入湖任务“归属部门”通过 `dts-platform` 目录接口获取
- [ ] sprint / feature / task / it 文档完整可追踪
