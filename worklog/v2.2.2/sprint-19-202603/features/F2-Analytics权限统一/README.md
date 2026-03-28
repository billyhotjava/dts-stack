# F2: Analytics 权限统一

**优先级**: P0
**状态**: READY

## 目标

改造 analytics 后端，废弃自有权限模型，统一通过 platform 权限 API 做判定。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | PlatformPermissionClient 与缓存 | P0 | READY | F1/T03 |
| T02 | PlatformPermissionFilter 请求拦截 | P0 | READY | T01 |
| T03 | 资产列表过滤集成 | P0 | READY | T01 |
| T04 | 清理遗留权限代码 | P1 | READY | T02, T03 |

## 完成标准

- [ ] 所有资产请求经 platform 权限判定
- [ ] 缓存策略生效（30s 单资产 / 60s 列表）
- [ ] 遗留代码清理完毕，无功能回退
