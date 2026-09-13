# DTS 标准内容包 Manifest v2 契约

**契约版本**：2.0  
**实现入口**：`StandardPackageManifestContract`  
**适用范围**：内置标准包、上传 ZIP、后续离线内容仓库制品

## 1. 有效样例

以下样例使用当前随产品交付的性别代码包真实摘要：

```json
{
  "schemaVersion": "2.0",
  "packageCode": "gbt-2261-gender",
  "packageName": "性别代码",
  "packageVersion": "1.0.0",
  "category": "公共码表",
  "industry": "COMMON",
  "releasedAt": "2026-07-23T00:00:00Z",
  "effectiveFrom": "2026-07-23",
  "dependencies": [],
  "replaces": [],
  "deprecated": false,
  "sourceRegisterRef": "SOURCE-REGISTER.json#gbt-2261-gender",
  "licenseConclusion": "REVIEW_REQUIRED",
  "files": {
    "03-reference-code-directories.csv": "fa2f302d1b3c0b724ff281d72784f15f361b3ffe4943cdce0e86828a3cf484fa",
    "04-reference-code-items.csv": "333d50fd357a99b9dc5c6a4d02ec13bce4db0a6e19aff14a4c4b01aa6f634661"
  },
  "contentChecksum": "21b5da1b33865a6572d3c1357f24e63d91a385468c180eb0ac732b2f444910d8"
}
```

`manifest.json` 不进入 `files`，避免自引用摘要。`contentChecksum` 是按文件名排序后，对每行
`<fileName>:<sha256>\n` 拼接结果再次计算 SHA-256；若调用方省略该字段，服务端计算并补齐，若提供则必须一致。

## 2. 字段语义

| 字段 | 必需 | 规则 |
|---|---:|---|
| `schemaVersion` | 是 | v2 固定为字符串 `2.0` |
| `packageCode` | 是 | 小写字母、数字和单连字符分段；是安装、依赖和升级的稳定身份 |
| `packageName` | 是 | 面向用户的包名称，不参与身份匹配 |
| `packageVersion` | 是 | `major.minor.patch` 三段非负整数 |
| `category` | 是 | 内容分类，如公共码表、数据元、术语 |
| `industry` | 是 | 适用行业；通用包使用 `COMMON` |
| `releasedAt` | 是 | UTC ISO-8601 时间，如 `2026-07-23T00:00:00Z` |
| `effectiveFrom` | 是 | ISO-8601 日期 |
| `dependencies` | 否 | `packageCode + minimumVersion`；默认空数组 |
| `replaces` | 否 | 被当前包替代的包编码；不得包含自身或重复项 |
| `deprecated` | 是 | 包是否已弃用 |
| `sourceRegisterRef` | 是 | 指向来源登记的稳定引用；登记实体和记录级落库由 F1-T03 实现 |
| `licenseConclusion` | 是 | `APPROVED / REVIEW_REQUIRED / REJECTED` |
| `files` | 是 | 包根目录文件名到小写 SHA-256 的映射；不能为空 |
| `contentChecksum` | 否 | 文件摘要清单的整体 SHA-256；服务端规范化后始终返回 |

额外展示字段（例如 `standardNo`、`description`）允许存在但不参与 v2 安装身份和摘要计算。

## 3. 依赖、版本和安装决策

| 场景 | 结论 | 错误码 |
|---|---|---|
| 所有依赖已安装且版本达到最低版本 | 允许继续预检 | - |
| 依赖未安装 | 阻断 | `MANIFEST_DEPENDENCY_MISSING` |
| 依赖版本过低 | 阻断 | `MANIFEST_DEPENDENCY_VERSION` |
| 目录存在循环依赖 | 阻断 | `MANIFEST_DEPENDENCY_CYCLE` |
| 候选版本高于已安装版本 | 允许继续预检 | - |
| 候选版本低于已安装版本 | 阻断 | `MANIFEST_VERSION_ROLLBACK` |
| 同版本、同内容摘要 | 允许幂等预检 | - |
| 同版本、内容摘要不同 | 阻断 | `MANIFEST_VERSION_CONTENT_MISMATCH` |
| Sprint-57 历史记录无版本元数据 | 按 `1.0.0` 兼容，未知内容摘要不触发同版本漂移判断 | - |

目录使用 Kahn 拓扑排序，依赖包始终先于消费包；同层按 `packageCode` 排序，构建结果可重复。
已安装状态从所有 `APPLIED` 记录解析，不限定 `BUILTIN / UPLOAD / MANUAL` 来源。

## 4. 文件完整性

- manifest 声明的每个文件都必须存在且 SHA-256 一致。
- 除 `manifest.json` 外，包中不允许出现未声明文件。
- 文件名只能是包根目录文件名，禁止 `/`、反斜杠和 `..`。
- Manifest 校验、依赖校验和版本校验均发生在 CSV 解析及 run 持久化之前。
- T02 验证内容完整性；离线 ZIP 的非对称签名、签名密钥和发布许可门禁由 F4-T01 与 F1-T04 实现。

## 5. 兼容策略

| 输入 | 处理 |
|---|---|
| v2 内置目录 | 严格解析、校验依赖图，按拓扑顺序展示 |
| v1 classpath 目录（`code/name/category`） | 通过 `parseCatalogCompatible` 显式适配为 `schemaVersion=1.0` |
| 带 `manifest.json` 的上传 ZIP | 按 v2 严格校验 |
| 不带 `manifest.json` 的上传 ZIP | 显式适配为 `LEGACY_V1`，版本为 `1.0.0`，摘要由实际文件计算 |
| 不支持的显式 schema | 拒绝，不静默降级 |

兼容模式会写入 preview 和 payload 的 `manifest.compatibilityMode`。旧包兼容不代表可发布；
`licenseConclusion` 在 T02 仅作为必需元数据保存，只有 F1-T04 才负责执行 `APPROVED` 发布门禁。

## 6. 稳定错误码

| 类别 | 错误码 |
|---|---|
| JSON/schema | `MANIFEST_INVALID_JSON`、`MANIFEST_SCHEMA_UNSUPPORTED`、`MANIFEST_REQUIRED_FIELD`、`MANIFEST_SERIALIZATION_FAILED` |
| 包身份/时间/许可 | `MANIFEST_PACKAGE_CODE_INVALID`、`MANIFEST_VERSION_INVALID`、`MANIFEST_RELEASED_AT_INVALID`、`MANIFEST_EFFECTIVE_FROM_INVALID`、`MANIFEST_LICENSE_CONCLUSION_INVALID` |
| 文件/摘要 | `MANIFEST_FILE_NAME_INVALID`、`MANIFEST_FILE_MISSING`、`MANIFEST_FILE_UNDECLARED`、`MANIFEST_CHECKSUM_INVALID`、`MANIFEST_CHECKSUM_MISMATCH`、`MANIFEST_CONTENT_CHECKSUM_MISMATCH` |
| 依赖 | `MANIFEST_DEPENDENCY_INVALID`、`MANIFEST_DEPENDENCY_SELF`、`MANIFEST_DEPENDENCY_DUPLICATE`、`MANIFEST_DEPENDENCY_MISSING`、`MANIFEST_DEPENDENCY_VERSION`、`MANIFEST_DEPENDENCY_CYCLE` |
| 替代/目录 | `MANIFEST_REPLACES_INVALID`、`MANIFEST_REPLACES_SELF`、`MANIFEST_REPLACES_DUPLICATE`、`MANIFEST_DUPLICATE_PACKAGE`、`MANIFEST_LEGACY_PACKAGE_INVALID` |
| 安装版本 | `MANIFEST_VERSION_ROLLBACK`、`MANIFEST_VERSION_CONTENT_MISMATCH` |

## 7. 阶段边界

T02 只把包级 Manifest 摘要写入现有 `std_pkg_import_run.preview_json/payload_json`，不新增数据库列。
术语、数据元、码表目录、码值和映射的记录级来源、基线版本与稳定记录键由 F1-T03 建立；
客户覆盖保护属于 F2，来源许可证发布门禁属于 F1-T04。
