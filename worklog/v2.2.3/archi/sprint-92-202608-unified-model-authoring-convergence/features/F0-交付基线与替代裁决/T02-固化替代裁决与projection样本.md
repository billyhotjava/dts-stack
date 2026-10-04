# T02：固化替代裁决与 projection 样本

**优先级**：P0  
**状态**：READY  
**依赖**：T01 的样本 ID；文档替代登记可先执行

## 目标

明确 Sprint-91 哪些交付事实保留、哪些产品语义被 Sprint-92 替代，并对平台生成、手工代码、ZIP 三类 bundle 建立 FULL/PARTIAL/NONE 的安全投影基线。

## 技术设计（Contract-first）

- **输入契约**：Sprint-91 ADR-91-02/03/09、F2/F3/F5、handoff；三类隔离 bundle；账本 L02～L06、L12～L15。
- **输出契约**：Sprint-91/93/queue 的 `SUPERSEDED_BY_SPRINT_92` 交叉引用；projection inspect 报告 `{sampleId,sourceKind,lossless,fileCount,totalBytes,coverage,rawNodeCount,reasons[]}`。
- **数据流**：bundle static inspect → 既有 parser/validator → projection summary；只读，不 freeze/commit。
- **错误路径**：歧义或动态宏默认 `PARTIAL/NONE + raw node`；不得为了提高 FULL 比例改写样本。
- **复用点**：`DbtSqlProjectionParser`、`AdvancedDbtDraftStaticValidator`、Sprint-91 handoff。

## 影响范围

Sprint-91/93/queue 交叉引用及 Sprint-92 证据；不修改旧 Task 的历史完成状态。

## 验证（RED→GREEN）

- [ ] 文档检查：全仓 Sprint-92 描述不再只写“安全回切”。
- [ ] inspect：三类样本均返回稳定 coverage/reasons，重复运行 checksum 一致。
- [ ] 安全：报告不含 SQL/YAML 正文。

## Definition of Done

- [ ] ADR-91-09/F2/F3/F5 被替代范围清楚，未受影响能力保留。
- [ ] OQ-02 关闭，F2 golden case 输入可直接复用。
- [ ] 无历史证据或任务状态被倒改。

