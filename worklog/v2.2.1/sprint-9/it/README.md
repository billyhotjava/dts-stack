# Sprint-9 集成测试与演示验证

本目录用于项目看板系统的验证入口、测试数据说明与演示流程。

## 验证范围

- 项目主体域 Excel/CSV 测试批次是否可稳定生成
- 映射表 seeds 是否可导入
- 项目主体域批次 / ODS / 问题表链路是否可落地
- dbt 语义层与 ADS 是否能正确生成
- `dts-analytics` 项目看板聚合 API 是否返回稳定结构
- `/analytics/project-cockpit` 是否能进入统一壳页
- 5 个主题视图是否可切换且上下文不丢失
- 树状进度看板、甘特图、趋势图是否可稳定展示演示数据

## 测试数据文件

- [project-cockpit-test-batch-2000.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-9/it/project-cockpit-test-batch-2000.xlsx)
- [generate_project_cockpit_test_data.py](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-9/it/generate_project_cockpit_test_data.py)

文件说明：

- `1` 个主工作表 `project_progress_raw`
- `2000` 条数据行
- `5` 个重大项目
- `30` 个子项目
- 状态分布：
  - `1000` 条延期
  - `600` 条正常
  - `400` 条提前
- 刻意混入可容错脏数据：
  - 日期格式混杂
  - 风险等级脏值
  - 子项目名称空格/别名
  - 延期原因自由文本
  - 少量责任科室空值

重新生成命令：

```bash
python3 worklog/v2.2.1/sprint-9/it/generate_project_cockpit_test_data.py
```

## 已验证命令

### 0. 测试 Excel 生成

```bash
python3 worklog/v2.2.1/sprint-9/it/generate_project_cockpit_test_data.py
```

结果：
- 生成 `project-cockpit-test-batch-2000.xlsx`
- 工作表 `project_progress_raw` 共 `2001` 行（含表头）
- 状态分布满足 `50% / 30% / 20%`

### 1. dbt seeds 与模型

```bash
docker exec dts-dbt sh -lc 'cd /opt/dbt && dbt seed --profiles-dir /root/.dbt --select pm_dim_major_project_seed pm_dim_subproject_seed pm_map_node_subject_seed pm_dim_delay_reason_seed'
docker exec dts-dbt sh -lc 'cd /opt/dbt && dbt run --profiles-dir /root/.dbt --select pm_dim_major_project pm_dim_subproject pm_dim_delay_reason pm_map_node_subject biz_dwd_project_node_enriched biz_dws_week_subproject_summary biz_ads_major_project_overview biz_ads_major_project_tree_snapshot biz_ads_delay_reason_trend'
docker exec dts-dbt sh -lc 'cd /opt/dbt && dbt test --profiles-dir /root/.dbt --select pm_dim_major_project pm_dim_subproject pm_dim_delay_reason pm_map_node_subject biz_dwd_project_node_enriched biz_dws_week_subproject_summary biz_ads_major_project_overview biz_ads_major_project_tree_snapshot biz_ads_delay_reason_trend'
```

结果：
- 4 个 seed 成功入库
- 9 个新增/关联模型 `dbt run` 通过
- 37 个数据测试 `dbt test` 通过

### 2. 后端 API

```bash
cd source/dts-analytics
mvn -Dtest=ProjectCockpitResourceIT test
```

结果：
- `ProjectCockpitResourceIT` 通过，覆盖 summary、tree、risk-attribution、empty summary 和其他主题接口。

### 3. 前端测试与构建

```bash
cd source/dts-analytics-webapp/modern
node --import tsx --test \
  src/pages/project-cockpit/projectCockpitQueryState.test.ts \
  src/pages/project-cockpit/useProjectCockpitQueryState.test.ts \
  src/pages/project-cockpit/views/OverviewTrendView.test.ts \
  src/pages/project-cockpit/views/RiskAttributionView.test.ts \
  src/pages/project-cockpit/views/MajorProjectTreeView.test.ts \
  src/pages/project-cockpit/views/ExecutionView.test.ts \
  src/pages/project-cockpit/views/DataSupportView.test.ts \
  src/pages/screens/hooks/cardDataMapper.test.ts

pnpm typecheck
pnpm build
```

结果：
- 项目看板 helper/query-state/gantt 映射测试通过
- `pnpm typecheck` 通过
- `pnpm build` 通过，已产出 `ProjectCockpitPage-*.js/.css`

## 路由与专题页手工验证

启动 `analytics modern` 后，重点检查：

- `/analytics/project-cockpit`
- 默认进入 `总览趋势`
- 切换到 `计划执行`、`风险归因`、`重大项目树`、`口径支撑`
- 切换视图时 URL query 与筛选条件保持一致
- 树状进度看板支持重大项目、子项目、节点切换

## 演示建议顺序

1. 从首页或左侧导航进入 `项目看板`
2. 说明客户侧正式输入是 Excel/CSV，当前验证批次使用 `project-cockpit-test-batch-2000.xlsx`
3. 在 `总览趋势` 先展示 KPI、趋势和重点预警
4. 切到 `计划执行` 展示甘特板、里程碑和责任科室负载
5. 切到 `风险归因` 展示延期主因结构、周趋势和责任矩阵
6. 切到 `重大项目树` 演示重大项目 -> 子项目 -> 节点穿透
7. 最后进入 `口径支撑`，说明当前批次覆盖度、异常量和待客户补充项

## 补充材料

- [demo-checklist.md](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-9/it/demo-checklist.md)
