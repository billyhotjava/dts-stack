# IT-02 result

**结论**：PASS_WITH_GAPS（更正后的严格只读旅程通过；完整写入旅程未验收）

- 已部署的是 DTS 主线 `dts-platform-webapp`，不是 `worklog/prototype` Demo。
- 页面渲染真实 canonical ModelSpec 的单页逻辑画布与紧凑字段表。
- 更正后的 Playwright 把所有建模 API 4xx/5xx 视为失败，并在请求发出前阻断非安全方法；
  T01 单页旅程与 T04 工作台上下文旅程合计 2/2 通过，`/api/modeling/**` 写请求为 0。
- T04 已证明资产 URL 刷新恢复、服务端计划校正、阶段抽屉路由、未保存输入保留，以及返回时
  精确清理资产参数；没有引入第二套模型状态或 API owner。
- 初次 `3 passed` 由不完整的网络门禁产生，已撤销；当前截图由严格旅程重新生成并已遮蔽
  模型标题、门禁内容和字段数据。
- plan policy 404 已确认源于临时身份合同错误，不是策略数据缺失；未放宽产品权限。
- 当前 realm 缺少 `ROLE_INST_DATA_OWNER`/`ROLE_EMPLOYEE`，本轮只能使用临时
  `ROLE_OP_ADMIN` 配合写屏障，属于环境角色基线缺口。
- 本旅程由浏览器网络写屏障强制只读，因此不替代“新建业务维度 → CURRENT → DIMENSION KEY
  映射”的写入验收；该完整业务旅程仍留在 F2/T02。

截图：`model-detail-single-page-and-compact-fields.png`、
`model-workbench-asset-context.png`；两者均遮蔽模型标识、门禁和表单业务内容。
