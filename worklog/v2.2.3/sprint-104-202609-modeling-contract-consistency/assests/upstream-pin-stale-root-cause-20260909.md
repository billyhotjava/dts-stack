# 汇总表首次上游实现锁定缺失：Chrome 取证

时间：2026-09-09，外部浏览器通过 Playwright 连接操作；账号 xiezm。

## 复现

打开现有“项目进度汇总”r2（8898d08d-2ac2-47c8-b6e8-22bd6425e20b）。上游保持“0909项目任务快照明细v1”r3。重新打开时目标表为空，按用户截图填写 dws_prjtest_090901，点击“提交实现并继续”。

实际失败请求为 authoring-drafts/a4738134-4dbf-4654-b000-4e04398602a1 的 PUT 保存，HTTP 409，MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE；关联 ID b3618a5c-1bf9-491a-9bcf-5086bc65cd6a。未进入实现提交阶段。

## 根因证据

- 请求 visualImplementation.inputs 仅有 modelSpecId、revision=3、checksum；缺少 implementationRevision、implementationChecksum、dbtUniqueId。
- 上游生命周期响应为 ACTIVE、implementationRevision=1。其模型版本和校验值与请求一致；实现版本、实现校验值和 dbtUniqueId 与原草稿 sourceBundle.dependencySnapshot 一致。
- 因此本次复现是缺失首次实现锁定信息，不能解释为已锁定版本发生变化。后端解码缺失版本为 0，统一草稿保存未先执行现有首次锁定策略，直接进入严格依赖比较而产生误报。
- UI 的“提交实现失败”包含了前置保存失败，不能据此判断实现提交接口失败。

## 修复及边界

复用 ModelImplementationInputPolicy 首次锁定策略；在可视化草稿保存、编译前准备输入，持久化锁定后的快照并返回给前端。已有锁定不得自动升级，真实漂移仍须拒绝。当前源码修改尚未提交、部署或验收。

前端同时保留快照中的 authoringImplementationInputs，后续保存优先使用这份输入锁定；implementationBase 仍指向已提交实现，用于既有版本并发检查。这样既避免首次保存后丢失锁定，也不把未提交快照冒充为已提交实现。

RAW_SQL 和四项投影提示来自初始化骨架；本轮未证明其为独立缺陷。初次打开直接提交另返回 DBT_DRAFT_FILES_REQUIRED（422）；填写截图目标表后才产生上述 PUT，记录但不扩大本轮修复结论。保存通过后仍需检查用户的字段映射和聚合配置，不能宣称汇总模型已完整可交付。

未修改数据库、上游模型或字段映射；保留汇总页面未保存输入，取消了离开页面的放弃修改确认。登录提示同账号其他会话下线。

原始请求体、响应及版本对比见 [JSON 证据](upstream-pin-stale-browser-evidence-20260909.json)。原用户关联 ID 527aa4a4-02e6-4cc1-bae2-0f555794d3c1 和 f4059399-3aa6-4302-b129-0d3c27000fbd 未直接取到历史请求；结论基于同一现有模型的新复现。
