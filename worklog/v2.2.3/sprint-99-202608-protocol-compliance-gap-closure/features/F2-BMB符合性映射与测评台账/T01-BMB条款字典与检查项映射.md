# T01: BMB17.x 条款字典与检查项映射

**优先级**: P0
**状态**: DRAFT（阻塞于条款目录来源，见下）
**依赖**: F0/T01

## 目标
把现有 6 项笼统安全基线检查（账本#7）升级为按 BMB17.1/17.2-2024 条款号组织的检查项字典，使每条检查都能回答"对应哪一条条款"。

## 技术设计 (Contract-first)
- **输入契约**: 甲方/测评机构提供的 BMB17.1-2024、BMB17.2-2024 条款目录（条款号 + 标题 + 要求摘要）
- **输出契约**:
  - `security_baseline_remediation` ALTER：`standard_code varchar(32)`、`clause_no varchar(32)`、`assessment_round int`（均可空，Expand-only）
  - `SecurityBaselineService` 检查项定义扩展字段：`standardCode`、`clauseNo`、`applicability(APPLICABLE|NA)`、`naReason`
  - 既有 6 项检查全部映射到对应条款；`verifyMode` 能自动化的改为 `AUTO`（交 T02 实现）
- **数据流**: 检查项定义（代码内常量 → 可 seed 到表）→ `/api/security/baseline/checks?standard=&round=` 返回
- **错误路径**:
  - 条款目录未到位 → 本 Task 保持 DRAFT，**不得自行编造条款号**（编造的合规映射比没有更危险）
  - 某条款平台不适用（如涉及物理环境）→ 标 `NA` 并**必填** `naReason`，测评时可解释
- **复用点**: `SecurityBaselineService`（账本#7）常量表结构；**禁止新建 compliance 模块**（ADR-99-02、domain-dts A4）
- **实现方案**:
  1. 先落表结构与"标准 + 轮次"骨架（不阻塞）
  2. 条款目录到位后，录入条款字典 seed
  3. 6 项既有检查逐条挂条款号
  4. 新增条款中平台可自证的部分，标记 `AUTO` 交 T02

## 影响范围
- `dts-platform`：`service/security/baseline/SecurityBaselineService.java`、`domain/security/SecurityBaselineRemediation.java`、新 changelog `sprint99-baseline-bmb.xml`
- **改动前须跑 `gitnexus_impact({target:"SecurityBaselineService"})`**（domain-dts A1）

## 验证 (RED→GREEN)
- [ ] 契约测试：`GET /checks?standard=BMB17.1&round=1` 返回带 `clauseNo` 的条款集
- [ ] 迁移测试：干净库上执行 changelog 成功；已有数据的库执行后旧行 `clause_no` 为 NULL 不报错
- [ ] 边界：`applicability=NA` 且 `naReason` 为空 → 拒绝保存

## Definition of Done
- [ ] 架构：契约测试绿 + 迁移在干净 DB 与存量 DB 各执行一次
- [ ] UI：条款表能渲染（与 T04 联调）
- [ ] 切片：真实实例上打开页签能看到条款
- [ ] 无编造条款号
