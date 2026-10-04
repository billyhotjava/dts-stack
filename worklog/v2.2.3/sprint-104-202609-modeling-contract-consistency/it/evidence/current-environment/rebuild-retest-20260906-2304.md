# 容器重建后的页面复验

日期：2026-09-06 23:03–23:07（Asia/Shanghai）。用户已重建容器，本轮未构建、部署或修改业务代码。

## 环境

- 开发/部署目录 HEAD：28eca691d4b78fa90466e94f27ea23496fa45d6e。容器镜像未携带源码提交标签，故该 SHA 是部署目录版本，不能独立证明镜像构建来源。
- platform：sha256:99ceb9c3503c86cbf524a2118664d30962839dabb2af4cb1413e39535fe64190，healthy。
- webapp：sha256:0b782c035991475385b4b7ad231968554c949312341c01a7bbe7eebbe4568262，running。
- analytics：sha256:c9fb43446594aa3e311e852e8b53e3f710100865feacfa06f6acaa61ad9eb280，healthy。
- 当前登录仍有效，已完整刷新前端。未核对本轮正式交付包及离线安装，不宣称该阶段完成。

## 实际页面场景

独立样例：S104 无时间明细复测，modelSpecId=e71715b7-ad1d-49b5-86e2-a91fa91e1b07，r2/实现r1，test。

1. **目录重试 FAIL**：在模型页点击“重试同步”，页面先进入目录同步中；23:04:01后台最终为 SYNC_FAILED / ANALYTICS_SEMANTIC_PUBLISH_HTTP_400。Analytics实际收到 tableName=dwd_s104_detail_retry、schemaName=public、dataSourceName=null，HTTP400。平台correlationId=121c5c52-e026-4413-b4ed-f6834b02f4e6；Analytics请求ID=2b7f55c7-4d57-4329-bed2-c50634ca6f25。
2. **再次运行 BLOCKED**：从列表该行“物化历史 / 再次物化”进入，选择测试环境，发布模型页确认test/MANUAL_ONLY后点击“立即运行并核验”。页面返回 MODEL_OPERATIONAL_RUN_CONCURRENT / Another operational run is active for this plan binding。未产生成功运行证据。只读查到test binding=69949db3-d42d-3870-ba0e-39e6e238e184，对应已有dispatch=d727145f-2b91-3fb7-882e-37af67443cbc，UNKNOWN / MODEL_OPERATIONAL_DISPATCH_RECOVERY_FAILED。并发拒绝本身属于保护；旧派发未收敛导致验收仍阻塞。没有强制清理、改状态或修复部署。
3. **跨模型证据展示 FAIL**：上述弹窗资产登记表仍为e717样例，但“发布模型”显示“尚无候选 / 当前计划候选不包含所选模型”，同时展示另一模型prjdemo_dwd_project_task_snapshot的治理质量通过证据，5/7项通过。错误混用的规则版本06bddc5f-cd83-41df-8142-00633d687c83、绑定8729d815-c811-4f50-af6c-0b61964c9eb6、运行b5325e5f-d3eb-4ad2-ab16-4d1e5b8d2c6a。应按所选模型候选隔离证据，不能以此判定e717质量通过。

现有物化卡仍显示旧17:07:36的BUILT/关系已核验；该历史记录不代表本轮再次运行成功。本轮只执行上述定向页面场景，未扩展到其他用户模型，未宣称Sprint整体验收通过。
