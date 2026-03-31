# T04: 删除modern前端代码并更新发布兼容清单

**优先级**: P1  
**状态**: READY  
**依赖**: T01,T02,T03

## 目标

在所有运行时和基础设施依赖切断后，正式删除 `dts-analytics-webapp/modern` 前端代码与镜像构建物，并更新发布兼容清单。

## 技术设计

- 删除 `source/dts-analytics-webapp/modern` 与 `builds/dts-analytics-webapp/modern`。
- 清理剩余 docs、release checklist、测试 fixture 中的 `modern` 前端指向。
- 保留 `dts-analytics` 后端服务，确认其 API 仍被 platform 调用。

## 影响范围

- `source/dts-analytics-webapp/modern/**`
- `builds/dts-analytics-webapp/modern/**`
- `docs/**`
- `tests/**`

## 验证

- [ ] 仓库内不再存在对 `dts-analytics-webapp-modern` 前端服务的有效依赖
- [ ] analytics 功能通过 platform 入口可正常访问

## 完成标准

- [ ] modern 前端代码删除完成
- [ ] 发布兼容与回滚清单更新完成
