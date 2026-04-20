# 项目管理 v2 测试数据

覆盖 **8 个重大项目**，时间跨度 2025-2026，用于 dbt v2 模型链路验证。

## 8 个重大项目

| 项目编号 | 主题 | 启动时间 | 当前阶段 |
|---|---|---|---|
| `XM-2025-A01` | 综合控制系统 | 2025-01 | 初样 → 正样设计（2026） |
| `XM-2025-A02` | 发动机控制系统 | 2025-01 | 初样热试车（含振动归零） |
| `XM-2026-B01` | 制导系统 | 2026-01 | 初样研制（算法+硬件） |
| `XM-2026-B02` | 遥测遥控系统 | 2026-01 | 初样研制（天线+协议） |
| `XM-2026-B03` | 电源系统 | 2026-01 | 初样研制（蓄电池+配电） |
| `XM-2026-B04` | 结构系统 | 2026-02 | 初样研制（主承力） |
| `XM-2026-B05` | 测控系统 | 2026-02 | 初样研制（主机+软件） |
| `XM-2026-B06` | 综合试验系统 | 2026-03 | 鉴定阶段（EMC+可靠性） |

## 文件清单

| 文件 | 对应 ODS 表 | 行数 | 作用 |
|---|---|---|---|
| `ods_project_subject_domain_v2.csv` | `ods_project_subject_domain_v2` | 39 | **进度事实**（DWD 核心） |
| `ods_progress_measure_v2.csv` | `ods_progress_measure_v2` | 8 | 进度跟进措施 |
| `ods_quality_issue_v2.csv` | `ods_quality_issue_v2` | 15 | **质量事实**（DWD 核心） |
| `ods_quality_measure_v2.csv` | `ods_quality_measure_v2` | 8 | 质量跟进措施 |
| `ods_tech_state_v2.csv` | `ods_tech_state_v2` | 14 | **技术状态事实**（DWD 核心） |
| `ods_tech_state_measure_v2.csv` | `ods_tech_state_measure_v2` | 8 | 技术状态跟进措施 |
| `ods_risk_info_v2.csv` | `ods_risk_info_v2` | 18 | **风险事实**（DWD 核心） |
| `ods_risk_measure_v2.csv` | `ods_risk_measure_v2` | 8 | 风险跟进措施 |
| `ods_material_info_v2.csv` | `ods_material_info_v2` | 10 | 重要物料 |

## 数据语义约定

**枚举值（DWD 字典映射）**
- `completion_status`（完成情况）：`按时完成` / `正常待完成` / `超期已完成未变更` / `超期已完成已变更` / `超期未完成未变更` / `超期未完成已变更` / `不正常待变更`
- `node_type`（节点类型）：`一般节点` / `重要节点` / `重大节点` / `里程碑`
- `risk_level`（风险等级）：`高` / `中` / `低`
- `risk_category`（风险分类）：`技术` / `进度` / `成本` / `设计` / `质量` / `其他`（供应链/管理/资源会归入 `进度`）
- `change_category`（更改类别）：`I` / `II` / `III`
- `file_signature_status`（签署状态）：依赖 `change_category` 上下文
  - I/II 类：`已提出需求，未评估评审` / `已评估评审，未签署` / `已评估评审，已签署`
  - III 类：`已提出需求，未签署` / `已提出需求，已签署`
- `reform_status`（整改状态）：`已落实整改` / `整改中` / `不涉及`
- `issue_category`（质量原因）：`设计` / `工艺` / `管理` / `元器件` / `操作` / `外协` / `软件` / `环境` / `其他`
- `status`（质量归零状态）：`未完成归零` / `已完成技术归零` / `已完成管理归零` / `已完成技术和管理归零`
- `risk_status`（风险状态）：`已释放` / `未释放`（未命中默认 `未释放`）

**日期**
- 日期字段固定 `YYYY-MM-DD` 格式
- 周数字段是整数（1-52）

**项目间联动关系（便于端到端验证）**
- `XM-2025-A02` 热试车异常 → 同时生成 `risk_info_v2`、`quality_issue_v2`、`tech_state_v2`、`progress`、`material_info_v2` 关联记录
- `XM-2026-B01` 元器件失效 → `quality_issue` + `risk_info` + `tech_state` + `material_info` 形成闭环
- `XM-2026-B06` 可靠性与 EMC 问题 → 覆盖鉴定阶段重大风险情景

## 加载方式

```bash
cd worklog/v2.2.3/s10/v4/pjm/test
psql -d <your_db> -f load_test_data.sql
```

## 运行 dbt 链路

```bash
cd worklog/v2.2.3/s10/v4/pjm/dbt_model
dbt run --select pm_analytics_v3
dbt test --select pm_analytics_v3
```

## 关键验证场景

- **进度域**：`biz_ads_progress_kpi_v2` 应该能产出按时完成率/超期率等 33 个指标；`XM-2025-A02` 的热试车节点会进入 `is_overdue_incomplete_effective = true` 分支
- **质量域**：`biz_ads_quality_kpi_v2` 覆盖各项目的归零率；`XM-2025-A01` 软件崩溃问题已完成双归零
- **技术状态域**：`biz_ads_tech_state_kpi_v2` 按 I/II/III 分类统计签署与整改；`XM-2026-B02` 遥控协议尚未签署，落在未签署分支
- **风险域**：`biz_ads_risk_kpi_v2` 统计释放率；`XM-2026-B06` 可靠性失效风险尚未释放
