# T02: Feature Flag 前后端打通

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

建立 `enableSqlIdeV2` 的开关机制，支持灰度发布与一键回滚，确保关闭时与当前生产行为 100% 一致。

## 技术设计

### 后端

- `application.yml` 新增配置：
  ```yaml
  dts:
    sql-ide:
      v2:
        enabled: false
  ```
- 新增 `SqlIdeFeatureProperties` `@ConfigurationProperties`
- `GET /api/config/global` 返回中新增字段 `enableSqlIdeV2: boolean`（复用现有全局配置端点）

### 前端

- `GLOBAL_CONFIG` 类型扩展 `enableSqlIdeV2: boolean`
- 路由逻辑：
  ```tsx
  { path: '/explore/query', element: GLOBAL_CONFIG.enableSqlIdeV2 ? <SqlIdePage/> : <QueryWorkbenchPage/> }
  ```
- 若需要两个都能访问（灰度阶段），老路由保留 `/explore/query-legacy`

### 灰度策略

- 默认关闭
- 灰度阶段通过 `application-{profile}.yml` 按环境覆盖
- 前端对单个用户/角色的细粒度灰度留给后续（本 Sprint 不做）

## 影响范围

- `application.yml` 新增配置节
- 全局配置端点响应体新增字段
- 前端路由配置文件
- `GLOBAL_CONFIG` 类型定义

## 验证

- [ ] `enabled: false` 时，`/explore/query` 渲染老 `QueryWorkbenchPage`
- [ ] `enabled: true` 时，渲染新 `SqlIdePage`
- [ ] 切换配置后重启即生效，无需改代码
- [ ] 老页面功能完整无退化（手动烟测）

## 完成标准

- [ ] 后端配置项生效
- [ ] 前端路由条件渲染生效
- [ ] 灰度切换文档写入 `README.md`
