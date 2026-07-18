# T02：重构高级 dbt 工作区

- **状态**：READY
- **优先级**：P0
- **依赖**：T01
- **影响模块**：dbt files/project UI、compile/test/run client、菜单

## 目标

把 dbt 相关项目、文件、SQL、宏、编译、测试、运行和 manifest 集中到明确标注的高级工作区。

## 实施内容

1. 高级 dbt 菜单不进入普通规划必经步骤，但保持 plan/model 过滤上下文。
2. 项目树、编辑器、编译结果、测试和运行记录分 Tab 组织。
3. 明确显示每个 dbt node 是否已绑定 ModelSpec，以及绑定状态/漂移。
4. manifest 导入先显示差异和候选，不自动覆盖逻辑模型。
5. 复用现有 vNext compile/run/artifact API，补足错误和权限状态。

## 验收标准

- dbt 概念不会出现在新手规划首屏；
- 未绑定 node 不伪装为正式模型；
- SQL/宏编辑和运行权限分离；
- manifest import 可预览、可取消、可重复；
- Chrome 95 编辑、编译、测试和错误定位可用。

## 验证证据

- menu/route tests；
- manifest preview contract tests；
- compile/test/run UI tests；
- 权限和错误截图。
