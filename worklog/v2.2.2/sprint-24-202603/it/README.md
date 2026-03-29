# Sprint-24 IT / 验收清单

## 自动化验证

- [x] `source/dts-platform` 编译通过
- [x] `source/dts-platform` 逻辑建模相关单测通过
- [ ] `source/dts-platform-webapp` 构建通过
- [x] `source/dts-platform-webapp` 逻辑建模相关测试通过

### 已执行命令

- `cd source/dts-platform && ./mvnw -q -Dtest=DbtOutputRelationServiceTest,DbtDagServiceTest,DbtWorkspaceBootstrapTest,EtlResourceTest test`
- `cd source/dts-platform && ./mvnw -q -Dtest=DbtReleaseSubmissionServiceTest test`
- `cd source/dts-platform && ./mvnw -q -DskipTests compile`
- `cd source/dts-platform-webapp && node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/modeling/modelingToolbar.helpers.test.ts src/pages/modeling/sqlModelBuild.helpers.test.ts src/pages/modeling/sqlModelOutputAction.helpers.test.ts`
- `cd source/dts-platform-webapp && pnpm build`

### 结果说明

- `source/dts-platform-webapp` 的建模相关测试通过。
- `清空产出表` 已改成 `dbt run-operation truncate_relation` 后台任务模式，验证覆盖了 DAG 生成、workspace 宏落地和接口提交流程。
- `上线` 提交链路已移除同步 `sources refresh`，验证覆盖了 warning 确认后的快速提交分支。
- `pnpm build` 仍被仓库既有 `echarts` / `echarts-for-react` 类型缺失阻塞，错误位于 `src/components/chart/*` 与 `src/pages/ops/OpsOverviewPage.tsx`，不属于本次 `F7` 改动范围。

## 人工回归

- [ ] 新建模型时同项目空间重名被拦截
- [ ] 更新模型名时重名被拦截
- [ ] 导入模型时重名被拦截或按规则覆盖
- [ ] 批量导入结果能区分成功/跳过/失败
- [ ] 左树多选、治理弹窗、未归档列表选择语义一致
- [ ] 单删、批删、治理、归档结果反馈正确
- [ ] 从 ODS 生成模板入口可用，旧的一键生成入口已下线
- [ ] 发布前编译、测试、上线步骤完整可执行
- [ ] `上线` 弹窗点击 `提交` 后能在短时间内得到结果，不再长时间只显示 loading
- [ ] `提交变更` 实际创建 Git commit，模型状态按预期更新
- [ ] `同步模型` 在 manifest 存在时同步成功，在 manifest 缺失时提示清晰
- [ ] `文档` 动作具备明确结果反馈，用户可判断是否生成成功
- [ ] `清空产出表` 可直接提交后台任务，不再依赖 `dts-platform` 直连目标数仓
- [ ] `重建产出表` 在目标数仓不可连时仍可直接进入 build 链路，在可连时行为不回退
