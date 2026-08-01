# T01：加固 ZIP、临时目录与非受信代码边界

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0/T02

## 目标

延续 SafeZipExtractor 基础，并证明上传、解压、source parse 和草稿 validate 不泄露明文、不下载依赖、不执行模型 SQL。

## Contract-first

- **输入限制**：Content-Length、ZIP 大小/条目/解压量/压缩比、SQL/宏/图上限。
- **临时目录**：受控 tmpfs/临时根；非预期挂载、不可写、清理失败按环境策略 fail-closed/告警阻断。
- **禁止内容**：profiles、私钥、凭据、nested archive；发现后拒绝且审计只记 reason code/checksum。
- **执行边界**：P0 只静态 parse/受控 compile；无 Git、packages download、dbt build、warehouse connection。

## 验证

- [ ] FX-05 全矩阵、进程中断、磁盘满、超时清理。
- [ ] 网络桩断言 inspect/preview 0 外联。

## Definition of Done

- [ ] ZIP/SQL 正文不进日志、审计、环境变量或宿主机可见长期目录。
