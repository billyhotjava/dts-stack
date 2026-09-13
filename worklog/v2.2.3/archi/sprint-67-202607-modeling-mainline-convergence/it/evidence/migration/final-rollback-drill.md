# Sprint-67 最终回滚演练

**日期**：2026-07-20  
**范围**：仅切换 `dts-admin`、`dts-platform`、`dts-platform-webapp` 镜像；不执行数据库降级、不恢复旧写、不物理删除数据。

## 镜像基线

| 服务 | Sprint 前镜像 | 最终候选镜像 |
|---|---|---|
| dts-admin | `sha256:9cc7dc2a...` | `sha256:2ae09be...` |
| dts-platform | `sha256:38822cff...` | `sha256:176b1ee...` |
| dts-platform-webapp | `sha256:8bd09b65...` | `sha256:f011266...` |

最终候选另标记为 `sprint67-final-candidate-20260720`，Sprint 前版本使用 `pre-sprint67-20260720`，避免依赖未解析的浮动目标。

## 回滚与恢复结果

1. 回滚窗口：18:19:30—18:20:28，共 58 秒。
2. 三个服务切换到 Sprint 前镜像；admin/platform health 为 healthy，webapp 可访问，首页和 `/management/health` 均返回 200。
3. 恢复窗口：18:20:59—18:21:57，共 58 秒。
4. 三个服务切回最终候选；admin/platform health 为 healthy，webapp running，首页和 `/management/health` 均返回 200。

## 数据对账

切换前、回滚后和恢复后以下事实一致：

- ModelSpec `33030d7c-d3a5-459b-a3ba-facdbfaf920f` 均为 `PUBLISHED`、revision 3、checksum `215901de...`；
- migration batch 数量为 1；
- legacy `semantic_business_object` 记录数量为 5。

结论：镜像回滚不会丢失目标 ModelSpec、迁移台账或只读兼容数据，也不会恢复旧双写。退出门禁仍为 `NO-DROP`，因此没有执行数据库恢复或物理结构回滚。
