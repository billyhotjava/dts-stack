# T01：canonical 页面与路由收敛

**优先级**：P0  
**状态**：PASS_WITH_GAPS（源码、菜单 migration、部署、菜单/镜像回滚和工作台刷新 IT 通过；Chrome95 返回键待补）
**依赖**：F1～F4

## 目标

抽取业务面板后删除重复页面布局，并让旧 canonical 深链带上下文进入统一工作台。

## 技术设计

- **映射**：plans→`module=planning`；dimensions/models→`module=models` + asset；metric-workbench→`module=metrics`。
- **参数**：保留 `planId,modelSpecId,domainId,revision,returnTo`，非法外部 returnTo 丢弃。
- **输出**：replace redirect，避免浏览器返回循环。
- **删除**：只有在面板已由 Shell 消费且 route source-contract 通过时删除旧 Page shell。
- **错误路径**：无法解析 model/object 时进入显式 recovery，不静默跳首页。
- **菜单**：dts-admin 菜单种子收敛到 workbench；不删除原 menu id/role binding，先软删除。

## 验证

- [x] canonical deep link 的 plan/module/asset/indicator/baseline 参数映射与 stale lookup 测试。
- [x] 菜单、帮助链接、静态/动态 resolver 收敛，无新增旧入口。
- [x] Chrome 150 认证旅程模块切换与刷新不丢上下文。
- [ ] Chrome95 返回键/刷新不循环。

## Definition of Done

- [x] IT-08 菜单软删除与回滚段通过；兼容路由物理删除仍归 T02 观测门禁。
- [x] 重复页面入口已收敛，专业 SQL/dbt 路由仍可达。

## 实现证据

- 提交：`82d6e8eec`。
- 前端兼容/路由/菜单 source tests 与 TypeScript 审查通过；Liquibase/JSON 结构校验与数据库审查通过。
- 运行态：5 个旧菜单仅软删除，menu id 与每行 visibility binding 保留；定向 rollback/restore 均命中 5 行，platform/webapp/admin 旧镜像回切与候选恢复健康。
