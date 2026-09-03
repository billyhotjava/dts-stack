# PRJDEMO 证据登记表

本文件只登记真实发生的对象、版本和运行结果。没有证据时填写 `NOT_PROVED`，不要预填虚构 UUID。

## 1. 执行上下文

| 项目 | 实际值 |
| --- | --- |
| DTS 环境地址 | |
| DTS 版本/构建标识 | |
| 执行人 | |
| 角色/部门 | |
| 开始时间 | |
| 结束时间 | |
| 浏览器版本 | |
| 当前建模上下文 ID | |
| 当前规划方案 ID/版本 | |
| 默认数据湖 ID/名称 | |
| 目标数据源 ID/名称 | |

## 2. 环境基线

| 检查项 | 状态 | 证据/截图 | 备注 |
| --- | --- | --- | --- |
| 登录 | | | |
| 服务健康 | | | |
| 数据源可写 | | | |
| 默认数据湖 | | | |
| 数据分级权限 | | | |
| 建模上下文/规划方案 | | | |
| 标准与单位 | | | |

## 3. 对象和版本

| 阶段 | 对象 | 实际 ID | 版本/状态 | 证据/截图 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 文件接入 | PRJDEMO 项目任务快照导入 | | | | |
| ODS 资产 | ods_prjdemo_project_task_snapshot | | | | |
| 业务分类 | PRJDEMO_PROJECT_MGMT | | | | |
| 数据域 | PRJDEMO_PROJECT | | | | |
| 业务过程 | PRJDEMO_PROJECT_PROGRESS | | | | |
| 规划来源绑定 | ods_prjdemo_project_task_snapshot | | `CONFIRMED · CURRENT` | | 记录绑定 ID、确认版本和验证时间 |
| 标准 | PRJDEMO_TASK_SNAPSHOT_ID/实际复用编码 | | | | |
| 标准 | PRJDEMO_PROJECT_SNAPSHOT_ID/实际复用编码 | | | | |
| 标准 | PRJDEMO_PROGRESS_PCT/实际复用编码 | | | | |
| 标准 | PRJDEMO_ACTUAL_COST/实际复用编码 | | | | |
| 标准 | PRJDEMO_TASK_COUNT/实际复用编码 | | | | |
| 码表 | PRJDEMO_TASK_STATUS/实际复用编码 | | | | |
| DWD ModelSpec | 模型名称：项目任务快照明细 | | | | 物理表名：prjdemo_dwd_project_task_snapshot |
| DWD 实现修订 | prjdemo_dwd_project_task_snapshot | | | | |
| 质量规则 | PRJDEMO_PROGRESS_RANGE | | | | |
| 质量任务 | 项目任务进度范围校验执行任务 | | | | |
| DWS ModelSpec | 模型名称：项目进度汇总 | | | | 物理表名：prjdemo_dws_project_progress |
| DWS 实现修订 | prjdemo_dws_project_progress | | | | |
| 原子指标 | PRJDEMO_AVG_PROGRESS | | | | |
| BI 看板 | 项目进度总览 | | | | |

## 4. 运行记录

| 顺序 | 运行类型 | 运行 ID | 输入版本 | 输出/候选版本 | 开始时间 | 结束时间 | 状态 | 关键统计 | 证据 |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | CSV → ODS | | prjdemo_project_task_clean.csv | | | | | 8 行 | |
| 2 | DWD 物化 | | ODS 当前版本 | | | | | 8 行 | |
| 3 | 干净数据质量检查 | | DWD 当前版本 | 规则版本 | | | | 失败 0 行 | |
| 4 | DWS 物化 | | DWD 当前版本 | | | | | 2 行 | |
| 5 | 指标发布/验证 | | DWS 当前版本 | | | | | 72.50/53.75 | |
| 6 | BI 发布/验证 | | 指标或 DWS 当前版本 | | | | | 2 项目/8 任务 | |
| 7 | 脏数据 CSV → ODS（可选） | | prjdemo_project_task_bad.csv | | | | | 8 行 | |
| 8 | 脏数据 DWD 物化（可选） | | ODS 脏版本 | | | | | 8 行 | |
| 9 | 脏数据质量检查（可选） | | DWD 脏版本 | 规则版本 | | | | 失败 1 行 | |
| 10 | 恢复 CSV → ODS（可选） | | prjdemo_project_task_clean.csv | | | | | 8 行 | |
| 11 | 恢复 DWD 物化（可选） | | ODS 恢复版本 | | | | | 8 行 | |
| 12 | 恢复质量检查（可选） | | DWD 恢复版本 | 规则版本 | | | | 失败 0 行 | |
| 13 | 恢复 DWS/指标/BI（可选） | | 恢复版本 | | | | | 预期值恢复 | |

## 5. 物理数据核对

| 表 | 行数 | 唯一键数 | 关键结果 | 核对方式/证据 | 结论 |
| --- | ---: | ---: | --- | --- | --- |
| public.ods_prjdemo_project_task_snapshot | | | 12 列；8 行 | | |
| public.prjdemo_dwd_project_task_snapshot | | | 12 列；8 行 | | |
| public.prjdemo_dws_project_progress | | | PRJ-A 72.50/91000；PRJ-B 53.75/51000 | | |

## 6. 血缘、权限和审计

| 范围 | 预期 | 实际结果 | 状态 | 证据/截图 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 血缘 | ODS → DWD | | | | |
| 血缘 | DWD → DWS | | | | |
| 血缘 | DWS → 指标/BI | | | | |
| 权限 | 有权用户可 read | | | | |
| 权限 | write/export 按实际授权 | | | | |
| 权限 | 无权用户拒绝或 NOT_RUN | | | | |
| 审计 | 文件接入事件 | | | | |
| 审计 | 模型生命周期事件 | | | | |
| 审计 | 质量规则与运行事件 | | | | |
| 审计 | 指标/BI 发布事件 | | | | |

## 7. 最终结论

| 项目 | 结论 |
| --- | --- |
| 主链路 | `PASS / FAILED / BLOCKED` |
| 负向质量演练 | `PASS / FAILED / NOT_RUN` |
| 恢复状态 | `CLEAN / DIRTY / NOT_APPLICABLE` |
| 未证明能力 | |
| 后续处理 | |
