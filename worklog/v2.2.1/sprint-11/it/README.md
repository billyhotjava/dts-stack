# Sprint-11 集成验证说明

本 Sprint 的集成验证目标，是确认“专题模板 + 数据绑定中心 + 运行时 source/vars 编译”已经替代专题特例机制。

## 验证范围

1. `project-management` 模板初始化
2. `plm-overview` 模板初始化
3. Excel/CSV 入湖到 ODS
4. ODS 结果绑定到专题逻辑实体
5. dbt `compile/test/build` 前 source/vars 自动编译
6. 发布门禁对缺失绑定的阻断
7. `analytics` 项目管理专题读取正式绑定结果

## 建议验证步骤

1. 检查专题模板与逻辑实体是否已初始化
2. 导入一批项目管理 Excel，确认 ODS 表存在
3. 在专题绑定中心将该 ODS 表绑定到 `project-management.project_subject_domain`
4. 执行 `dbt compile`
5. 执行 `dbt build`
6. 打开项目看板，确认显示正式数仓数据
7. 解绑该逻辑实体，再次执行发布门禁检查，确认被阻断
8. 对 `plm-overview` 做最小绑定并执行一次 `compile`

## 验证结果记录

- 模板初始化：DONE
- 项目管理绑定：DONE
- dbt 编译：DONE
- dbt 构建：DONE
- analytics 项目看板：DONE
- `PLM` 示例：DONE

## 实际执行命令

- `cd source/dts-platform && mvn -Dtest=TopicBindingServiceIT,TopicBindingRuntimeServiceTest,TopicBindingResourceIT test`
- `cd source/dts-platform && mvn -Dtest=DbtReleaseGateServiceTest,DbtSourceServiceTest,EtlResourceTest test`
- `cd source/dts-platform && mvn -Dtest=TopicBindingResourceIT test`
- `cd source/dts-analytics && mvn -Dtest=ProjectCockpitResourceIT test`
- `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/foundation/topicBindingCenter.helpers.test.ts src/pages/foundation/projectCockpitImportBinding.helpers.test.ts`
- `cd source/dts-platform-webapp && pnpm build`

## 结果说明

- `project-management` 与 `plm-overview` 模板可以通过专题绑定中心读取到
- 逻辑建模页和发布弹窗能看到专题绑定诊断，缺失必填绑定会进入发布门禁 blocker
- 项目主体域导入页在批次落库后可直接发起专题绑定，不必先跳到单独页面
- `analytics` 项目看板 `data-support` 已能看到专题绑定来源与缺失清单
