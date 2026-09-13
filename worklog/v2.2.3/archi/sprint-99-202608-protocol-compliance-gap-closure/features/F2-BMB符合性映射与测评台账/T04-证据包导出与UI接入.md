# T04: 证据包导出与 UI 接入

**优先级**: P0
**状态**: DRAFT
**依赖**: T02、T03

## 目标
测评人员点一次「导出证据包」，拿到一个可直接交付的 zip：条款清单、每项判定、机器证据原文、整改记录、生成时间与操作人俱全。

## 技术设计 (Contract-first)
- **输出契约** `GET /api/security/baseline/report?standard=BMB17.1&round=1&format=zip`
  - `format` 缺省 → 保持既有 JSON 报告行为（**向后兼容，不破坏既有调用方**）
  - zip 结构：
    ```
    BMB17.1-2024_第1轮_20260824/
      00-汇总.md                 条款总数/符合/不符合/不适用/生成时间/操作人
      01-条款清单.csv            条款号,控制项,判定,判定方式,校验时间,整改状态
      02-证据/{clauseNo}/        每条 AUTO 项的机器证据原文（config/log/query 快照）
      03-整改记录.csv            条款号,责任人,期限,状态,说明,关闭时间
      04-manifest.json           每个文件的 SHA-256，防篡改自证
    ```
- **数据流**: 读 `security_baseline_verification` + `security_baseline_remediation` → 组装 → 流式写 zip（不落临时明文文件到宿主机可见目录，domain-dts D5）
- **错误路径**:
  - 该轮次无数据 → 400 + 明确提示"请先执行校验"
  - 导出过程超时 → 分段流式输出，不整包驻留内存
  - 越权 → 403；导出动作**必须写审计**（导出属敏感动作，账本#16）
- **复用点**: 既有 `GET /report`；导出临时文件走 tmpfs 路径（账本#14 已配）
- **实施期确认**: 测评机构对证据包格式的要求（README 开放问题3）——若有指定格式，以其为准，本结构为兜底

## UI 交互规格
- 触发：BMB 页签右上「导出证据包」
- 交互：点击 → 按钮 loading + 进度提示 → 浏览器下载
- 四态：空（该轮无校验记录 → 按钮禁用 + tooltip"请先执行校验"）/ 加载（loading）/ 错误（toast 显示后端 message，不吞错）/ 成功（下载开始 + toast）
- 走查：1. 选标准与轮次 → 2. 点「重新校验」→ 3. 等判定刷新 → 4. 点「导出证据包」→ 5. 解压检查 4 个目录齐全

## 影响范围
- `dts-platform`：`SecurityBaselineResource` 扩展 + 导出服务
- `dts-platform-webapp`：BMB 页签工具栏
- 审计字典（导出动作码）

## 验证 (RED→GREEN)
- [ ] 契约测试：`format=zip` 返回 zip；不传 `format` 仍返回既有 JSON（兼容性断言）
- [ ] 内容测试：解压后 4 个目录齐全，`manifest.json` 的 SHA-256 与实际文件一致
- [ ] 空数据：无校验记录 → 400 且提示可读
- [ ] 审计：导出操作可检索
- [ ] UI 走查：五步走查全过程截图

## Definition of Done
- [ ] 架构：契约 + 兼容性测试绿
- [ ] UI：四态证据入 `it/evidence/`，Chrome95 下下载正常
- [ ] 切片：真实实例导出的 zip 由非开发人员打开确认可读
- [ ] 无占位证据
