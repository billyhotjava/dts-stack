# 旧取消候选关系核验误报修复

2026-09-06，目标模型 `39c5e6b7-0b35-4728-8877-273f6418da17`（S104 无时间明细），测试环境。

## 已确认原因

- 历史候选 `fa36a14c-7079-4317-a541-a010d976b7e1`，CANCELLED v4；历史运行 `fc22a616-3baf-3d24-a869-07b90bf8cf31`，16:20:12 结束，BLOCKED / MODEL_AIRFLOW_DAG_NOT_REGISTERED。
- 该候选没有物理关系观测记录；此前工作区把无观测的 BLOCKED 映射为关系 FAILED，不能据此认定实际目标表核验失败。
- 当前模型已是 r4，但弹窗将旧候选标注为“当前候选”，未显示证据对应修订，容易误读为当前修订失败。
- **更正此前按钮状态记录**：本轮在原页面实测 `isEnabled()` 为 true。“创建并运行”当前可点击，不能认定重建入口本身存在 bug；此次未修改候选创建或权限流程。

## 修复范围

源码提交 `5e00e914f`：无观测且 BLOCKED 的关系状态改 UNKNOWN；真实失败观测仍为 FAILED，运行状态及原因不变。弹窗区分历史候选，证据显示模型修订，将尚未执行的关系核验显示为“未完成核验”，并展示未启动原因。外部模型候选的阻断不再误显示为所选模型的阻断。

新增后端实际调用回归及前端页面行为回归，覆盖真实观测失败保留、旧取消记录展示、既有创建动作和运行中限制。正式验证结果如下。

## 正式测试与交付

- 部署目录已 fast-forward 至 `5e00e914f8ef6131232a265876a22658cf91ea88`，未复制开发目录未提交文件。
- 前端 ModelWorkbenchDialog.release.test.tsx：34 passed、5 skipped；后端 ModelMaterializationBatchContractTest：5 passed、0 failures/errors/skipped。
- 正式构建入口：`builds/dts-build.sh --image dts-platform dts-platform-webapp --opmanager-output /opt/prod/s10/deploy/data/sprint104-acceptance/release-5e00e914f`。
- 包：`dts-opmanager-upgrade-20260906-173220.tar.gz`；SHA-256 `9c9394a99d41385eea34e4585fd95605d09f6672787868563ba3740311e45d09`。
- platform image：`sha256:f1a739d68e4f41266d183d7d9a28e4423e2926e64ba64fa6e4ff857480c11161`。
- webapp image：`sha256:9153c95371b85fb66ca5d1f4f902a87c0e3ead65a301c50b871fc17299a33813`。
- 仅以上两服务正式 recreate；platform healthy，webapp running。其他服务容器身份未变；before/after 证据保存在上述部署交付目录。
- 提交时本机缺少 lefthook，hook 未执行；以上专项测试及正式构建独立执行。

## 真实页面复验 PASS

1. 正式版本完整刷新，打开旧模型“发布与物化”，选择测试环境。
2. 历史记录显示 `历史候选 CANCELLED · v4`、`S104 无时间明细 · r3`、`BLOCKED / 未完成核验`；原因“执行任务尚未就绪，构建未启动”，并提示不代表当前修订结果。
3. 点击现有“创建并运行”，当前 r4 成功创建新候选 `ba6c1b2b-a318-48d9-8fb9-298581348765`，进入 BUILDING。此为正常创建新候选，并非修改旧候选状态。
4. 最终页面为 `当前候选 BUILT · v3`，`S104 无时间明细 · r4`，目标 `biadmin.public.dwd_s104_detail`，运行 BUILT，关系已核验，attempt1，完成时间2026/9/6 17:38:30。
5. 只读验证运行 `0a48a41b-018c-36d6-8596-6e8fd264e58f`：派发 COMPLETED，Airflow四任务success，dbt执行成功；物理观测 verified=true，TABLE存在，两列project_id/remark，3行。详见同目录 blocked-relation-check.json。
6. 当前Chrome桌面1366x768及默认尺寸证据区、状态和底部操作可见；长目标关系通过水平滚动展示，未见文字重叠。窄屏390x844做了加载检查，但缩放后的截图不足以证明完整窄屏布局验收；已恢复默认尺寸。未运行Chrome95，不宣称其兼容验收通过。当前页面错误日志读取为空。

本次关闭“旧无观测BLOCKED误报关系FAILED”及其原模型重新物化分支。整个Sprint仍有独立的质量/目录/再次运行等未完成场景，不以本次结果宣称全部完成。
