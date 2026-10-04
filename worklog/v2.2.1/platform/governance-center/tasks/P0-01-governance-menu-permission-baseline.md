# P0-01 治理中心权限与菜单基线

`status`: `done`
`priority`: `P0`

## 目标

确保治理中心所有页面与操作（增删改、执行、发布）权限一致，避免“能看不能用/越权可用”。

## 后端实施点

1. 统一治理接口的 `@PreAuthorize` 策略与角色映射。
2. 清理匿名/弱校验接口，补齐审计 action。
3. 输出权限矩阵（接口 -> 角色 -> 动作）。

## 前端实施点

1. 菜单与按钮按权限点显隐。
2. 禁用态提供明确原因（无权限/状态不允许）。
3. 页面 403 与空状态文案统一。

## 验收标准

- 普通员工仅可查看；治理维护者可执行治理动作；管理员可全量管理。
- 所有治理写操作均有审计记录。

## 本轮落地结果

1. 前端新增统一权限钩子：`source/dts-platform-webapp/src/hooks/useModuleManageAccess.ts`，并在治理/资产相关页面接入写操作禁用与前置拦截。
2. 治理中心主要写入口（规则、工单、合规、指标、码表、术语、主题域、模板）已统一按 `governance.manage`/治理维护角色执行禁用控制。
3. 数据资产相关写入口（元数据采集执行、访问审批动作）已接入 `catalog.manage`/维护角色控制。
4. 编译校验通过：
   - `cd source/dts-platform-webapp && pnpm build`
   - `cd source/dts-platform && ./mvnw -DskipTests compile`
