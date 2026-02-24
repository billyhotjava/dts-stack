# P2-03 治理权限矩阵硬化

`status`: `done`
`priority`: `P2`

## 目标

明确治理中心角色权限矩阵，细化到菜单、接口、数据范围（部门/密级）。

## 范围

`AuthoritiesConstants`、`@PreAuthorize`、菜单角色配置、部门上下文过滤逻辑。

## 子任务

1. 产出治理角色-能力矩阵（读/写/审批/删除）。
2. 修正接口注解与服务层二次校验不一致点。
3. 新增权限回归用例（角色+部门+密级）。

## 验收标准

- 权限矩阵与系统行为一致。
- 关键写操作均有双重校验（注解+服务层）。
- 权限回归脚本可重复执行。

## 完成说明

- 写接口权限补齐：`POST /api/governance/quality/runs` 已增加 `@PreAuthorize`。
- 权限矩阵文档：`worklog/v2.2.1/platform/governance/report/p2-03-permission-matrix.md`。
- 最小回归脚本：`worklog/v2.2.1/platform/governance/scripts/run-p2-03-permission-smoke.sh`。

## 风险与回滚

- 风险：权限收紧影响既有用户。
- 回滚：灰度开关 + 例外白名单。
