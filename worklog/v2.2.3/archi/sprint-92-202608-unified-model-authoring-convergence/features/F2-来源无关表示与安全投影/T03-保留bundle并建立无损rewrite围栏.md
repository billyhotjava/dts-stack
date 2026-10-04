# T03：保留 bundle 并建立无损 rewrite 围栏

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：T01、T02、F1/T02

## 目标

确保 visual 编辑只改变明确受管节点/文件，ZIP/手工 bundle 的未知文件、宏和原始代码在跨视图保存后 checksum 不变。

## 技术设计（Contract-first）

- **输入契约**：draft source bundle、projection managedPaths/rawNodes、save request model snapshot/files、base bundle checksum。
- **输出契约**：新 bundle checksum、逐文件 before/after checksum、`managedChanges[]`；unmanaged 文件必须 bit-for-bit 保留。
- **数据流**：比较 base/current → 仅对 managed node 调 compiler/serializer adapter → 合并原 bundle → static validate → 暂存 files/projection。
- **错误路径**：unmanaged 变化 → 409 `MODEL_AUTHORING_UNMANAGED_FILE_CHANGED`；managed checksum stale → 412；生成后 validator 失败 → 422 且旧草稿不变。
- **复用点**：`DbtProjectBundleManifest`、`ModelingDbtCompiler`、static validator、existing file repository。
- **安全**：ZIP 正文不写审计/日志；路径和敏感文件规则原样继承。

## 影响范围

`DbtProjectBundleManifest` 的调用适配、`ModelingDbtCompiler` managed rewrite adapter、draft file merge/repository 和 golden bundle tests；不改未知文件内容。

## 验证（RED→GREEN）

- [ ] ZIP golden bundle visual edit 前后所有 unmanaged checksum 相同。
- [ ] 用户先在 code 修改 raw node，再 visual 保存，不覆盖 code 修改。
- [ ] 恶意路径、超限文件、敏感文件名仍被既有 validator 拒绝。

## Definition of Done

- [ ] 三种来源 round-trip 均可回溯 sourceKind/lossless/bundle checksum。
- [ ] visual 修改不格式化、重排或删除未知文件。
- [ ] 冲突明确可恢复，不产生半保存 bundle。
