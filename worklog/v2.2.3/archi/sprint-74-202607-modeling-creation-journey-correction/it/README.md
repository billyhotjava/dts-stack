# Sprint-74 集成验收计划

**状态**：PASS（2026-07-27）；逐项证据见 `evidence/acceptance-summary.md`。

| ID | Journey | 通过条件 | 证据文件 |
|---|---|---|---|
| IT-01 | 新建入口无默认类型 | 打开抽屉时未选任何类型，保存按钮不可完成 | PASS；`evidence/chrome95/` |
| IT-02 | 四类业务目的决策 | 四张卡均显示“适合/不适合/例子”；服务端按经典规则返回 layer，页面分别展示二者 | PASS；`evidence/chrome95/`、`evidence/api-postgresql.md` |
| IT-03 | 维度逻辑设计 | 无来源、无实现保存 DIMENSION 并达到 DESIGNED | PASS；`evidence/api-postgresql.md` |
| IT-04 | 事实逻辑设计 | 业务过程→粒度→TIME 字段→维度引用闭合，错误时间引用被就地提示 | PASS；后端/前端 contract tests |
| IT-05 | 当前阶段 blocker | DRAFT 只显示 DESIGNED 缺口；未来项不计当前 blocker | PASS；stage-gate/stage-projection tests |
| IT-06 | 普通数据实现 | DESIGNED 模型选择规划物理来源、映射、物理名和装载策略并验证 | PASS；`evidence/api-postgresql.md`、`evidence/chrome95/` |
| IT-07 | 高级 dbt 实现 | 从数据实现选择高级 dbt，锁定同一 modelSpec/revision/implementation revision | PASS；`evidence/dbt-lifecycle.md` |
| IT-08 | 发布结果 | 未发布显示空态与真实 compile timeline；只有真实 `physicalAssetRef` 才显示资产；无 dbt 编辑入口 | PASS；`evidence/chrome95/it-08-release-result-chromium95.png` |
| IT-09 | 安全纠错 | 隔离 FACT preview eligible；显式确认后追加 DIMENSION revision，旧 revision 可读；不修改用户模型 | PASS；`evidence/api-postgresql.md` |
| IT-10 | 治理阶段 | 标准/质量/密级不阻断 DESIGNED；按计划策略在 RELEASE_READY 精确阻断 | PASS；真实 gate + policy evidence |
| IT-11 | 兼容迁移 | 旧中文字段、sourceRefs/dependsOn、implementationPolicy 快照可读；dry-run/apply/rollback contract 可验证 | PASS；`evidence/api-postgresql.md` |
| IT-12 | 四态与兼容 | 创建/逻辑/实现/结果的正常、空与阻断态；Chrome95 无控制台/页面/请求错误，窄屏无溢出 | PASS；`evidence/chrome95/` |

## 最终验证命令类别

实施期在各 Task focused RED→GREEN 后，仅在 F1～F4 完成时集中执行：

1. dts-platform 定向/组合后端测试；
2. dts-platform-webapp Node source-contract；
3. `pnpm build`；
4. clean DB migration + rollback rehearsal；
5. 真实 Chrome95 + Spring Security + API + PostgreSQL + dbt E2E；
6. GitNexus `detect_changes` 范围审计。

上述六类证据已全部完成；执行明细见 `evidence/acceptance-summary.md`。
