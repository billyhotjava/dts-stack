# Sprint-67 IT 与交付证据计划

**状态**：IN_PROGRESS（F2-F5、F6-T01/T02 已关闭；F1 契约评审与 F6-T03/T04 尚未关闭）

## 1. 验收旅程

### Journey A：从业务目标开始

1. 创建 WarehousePlan；
2. 选择业务分类和分层策略；
3. 不创建业务对象，登记一个维度；
4. 创建明细表并填写“一行代表什么”、粒度键、来源和时间语义；
5. 关联字段标准；
6. 生成/接管实现并完成质量、审核和发布；
7. 从已发布模型创建原子指标和派生指标；
8. 返回工作台时九站投影和唯一下一步正确。

### Journey B：从现有资产开始

1. 从表或 dbt 节点创建计划；
2. 确认业务分类、分层和来源；
3. 生成维度/明细/汇总/应用候选；
4. 人工确认模型类型、粒度、键和用途；
5. 不经过业务对象页面完成实现、发布和指标引用。

### Journey C：旧深链与旧数据

1. 访问旧 `/modeling/semantic/objects?objectId=...`；
2. 权限不扩大地跳转到维度或模型目标；
3. legacyRef 可定位迁移后的 ModelSpec；
4. 旧写 API 明确拒绝并留下调用审计；
5. 回滚开关可恢复旧只读视图但不能恢复双写。

## 2. 自动化矩阵

| 层级 | 必测内容 | 证据 |
|---|---|---|
| Contract | ModelSpec 无 objectId、四类型门禁、WarehousePlan 基线 | Java/TS 单元测试输出 |
| API | 计划、分类、模型、门禁、迁移 dry-run、旧写拒绝 | 后端集成测试报告 |
| Migration | 幂等、校验和、冲突、孤儿、租户隔离、回滚 | SQL/服务 dry-run 报告 |
| UI source-contract | 菜单禁用退役词、路由映射、唯一主动作、参数白名单 | tsx/node 测试输出 |
| Browser | 两条主线、旧深链、权限、失败恢复、窄屏 | Chrome 95 截图/视频和日志 |
| Build | dts-platform、dts-admin 菜单、两个 webapp | Maven/pnpm 构建记录 |
| Scope | 预期模块和 execution flow | GitNexus detect_changes 报告 |

## 3. 必测输入边界

- 无 planId 浏览目录与发起创建；
- planId 无权限、domainId 无权限、domainId 已归档；
- FACT 缺粒度、来源或时间语义；
- DIMENSION 缺维度键；
- SUMMARY/APPLICATION 缺上游；
- revision 漂移、来源删除、标准版本漂移；
- 旧对象可自动迁移、需拆分、应归档三类；
- 同一迁移批次重复执行；
- API/网络失败、刷新、返回、窄屏和 Chrome 95。

## 4. 禁止回归

- 页面或菜单重新出现“业务对象/语义对象”客户术语；
- 新建模型请求继续写 `objectId`；
- 维度创建要求业务活动；
- 页面访问或 sessionStorage 被当成完成证据；
- 旧路由跳转丢失 planId/modelSpecId 或扩大权限；
- 指标、发布、运行或血缘因语义服务拆分失去真实后端记录。

## 5. 证据目录约定

实现阶段在 `it/evidence/` 下按 `contracts/`、`api/`、`migration/`、`frontend/`、`chrome95/`、`build/`、`gitnexus/` 分类保存。README 只索引真实文件；未产生证据的检查不得标记 DONE。

F6-T02 已集中完成一次后端契约批次、一次 PostgreSQL Testcontainers 集成、一次前端 production build 和一次 Chromium 95 定点回归。对应证据为 `backend-contract/model-lifecycle.txt`、`runtime/compile-test-publish-run.json`、`runtime/failure-repair-loop.md` 和 `chrome95/README.md`；真实部署 E2E 不由 mock 浏览器证据替代。

## 6. 发布决策

只有 G1-G5 全部通过才能将 Sprint 标记 DONE。若新主线可用但旧调用未归零，允许发布为“业务对象对外退役、旧表只读”，不允许宣称物理删除完成；退出项必须留在 F5 证据中持续追踪。
