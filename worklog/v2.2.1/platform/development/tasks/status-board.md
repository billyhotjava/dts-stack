# 任务看板（v2.2.1 / development）

| 任务 | 状态 | 负责人 | 备注 |
|---|---|---|---|
| P0-01 主流程收敛 | done | codex | ZIP UI/API/服务代码已清理 |
| P0-02 历史 API 清理 | done | codex | 3 个旧 CRUD 控制器已下线 |
| P0-03 dbt 最小参数化 | done | codex | UI+API+DAG 参数化已完成 |
| P1-01 SQL 异步分页 | done | codex | 已实现异步提交、轮询与服务端分页 |
| P1-02 脚本开发 MVP | done | codex | 已实现脚本资产/版本/运行与日志追踪 |
| P1-03 编排可观测入口 | done | codex | DAG 列表/运行记录/失败摘要/重跑已接入 |
| P2-01 dbt DevOps 包 | done | codex | compile/test/docs + 产物状态与失败摘要已接入 |
| P2-02 模型质量基线 | done | codex | 自动质量模板 + 发布前质量门禁提示已接入 |
| P2-03 语义契约对齐 | done | codex | 契约字段/版本/影响面 + 看板绑定展示已接入 |
| P3-01 GitOps/CI Gate | done | codex | 发布前 Git/Commit/构建证据门禁 + 审计追踪已接入 |
| P3-02 回归矩阵 | done | codex | x86/ARM 三模式已跑通，当前环境窗口无样本数据（统计为 0） |
| P3-03 运营指标体系 | done | codex | 指标接口+趋势页+项目空间枚举+结构化回滚口径已接入 |

## 本轮执行项

- 当前执行：`已结项（P0-P3 全部完成）`
- 本轮输出要求：
  - 影响文件清单
  - 回归命令清单
  - 风险与回滚说明
