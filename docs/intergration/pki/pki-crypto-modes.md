# 普密/商密对照表 + 典型配置示例

本文仅用于**现有实现的分析说明**，不涉及任何代码修改。适用范围：平台 PKI 登录（admin 验签 + platform 建会话），签名格式为 **PKCS#7**。

## 现状概览（基于当前代码）
- 前端提交 `originDataB64 + signDataB64 + certContentB64 + signType` 给 admin。
- admin 根据 `signType` 选择网关端口并验签（厂商 JAR 或 HTTP 网关）。
- `signType` 仅用于端口选择；算法由厂商网关/JAR 处理。

## 普密 vs 商密（对照表）

| 维度 | 普密（非国密） | 商密（国密） |
| --- | --- | --- |
| 常见算法家族 | RSA + SHA1/MD5 等 | SM2/SM3/SM4 等 |
| 典型 `signType`（由厂商/客户端决定） | `RSA` / `PM` / `PM-BD` 等 | `SM2` 等 |
| 当前代码对 `signType` 的处理 | `SM2/RSA/PM` → 走 **主端口** `dts.pki.gateway-port` | `SM2/RSA/PM` → 走 **主端口** `dts.pki.gateway-port` |
| 备用端口触发条件 | `signType` **不包含** `SM2/RSA/PM` 时 → 走 `dts.pki.gateway-alt-port` | 同左 |
| 签名格式 | PKCS#7（CMS SignedData） | PKCS#7（CMS SignedData） |
| 证书传递 | 通过 `certContentB64`（或内含于 PKCS#7） | 同左 |
| 验签执行者 | 厂商 JAR / HTTP 网关 | 同左 |
| `dts.pki.digest` 影响 | 仅影响 **厂商 JAR** 模式；网关模式不使用 | 同左 |

> 备注  
> - “普密/商密”主要是**算法与厂商网关侧的区分**，当前后端仅通过 `signType` 做端口路由。  
> - 若你们厂商对“普密/商密”采用不同端口或不同 `signType`，请以厂商文档为准。  

## 典型配置示例（仅示例，以厂商参数为准）

### 示例 A：商密（SM2）HTTP 网关

```bash
# .env / compose 环境变量示例
DTS_PKI_ENABLED=true
DTS_PKI_MODE=gateway
DTS_PKI_GATEWAY_HOST=10.10.10.10
DTS_PKI_GATEWAY_PORT=5000
DTS_PKI_GATEWAY_ALT_PORT=10009
DTS_PKI_DIGEST=SHA1
```

特征：
- `signType` 通常为 `SM2`（由客户端/中间件产生）。  
- 代码会将 `SM2` 路由到主端口 `5000`。  

### 示例 B：普密（RSA/PM）HTTP 网关

```bash
# .env / compose 环境变量示例
DTS_PKI_ENABLED=true
DTS_PKI_MODE=gateway
DTS_PKI_GATEWAY_HOST=10.10.10.11
DTS_PKI_GATEWAY_PORT=5000
DTS_PKI_GATEWAY_ALT_PORT=10009
DTS_PKI_DIGEST=SHA1
```

特征：
- `signType` 通常为 `RSA` / `PM` / `PM-BD`。  
- 当前实现对 `RSA/PM` **同样走主端口**。  

### 示例 C：厂商 JAR 模式（适用于普密/商密）

```bash
# .env / compose 环境变量示例
DTS_PKI_ENABLED=true
DTS_PKI_MODE=gateway
DTS_PKI_GATEWAY_HOST=10.10.10.12
DTS_PKI_GATEWAY_PORT=5000
DTS_PKI_GATEWAY_ALT_PORT=10009
DTS_PKI_VENDOR_JAR=/opt/dts/vendor/svs-uk_custom.jar
DTS_PKI_DIGEST=SHA1
```

说明：
- JAR 模式由厂商 SDK 执行验签，`DTS_PKI_DIGEST` 会影响签名摘要算法选择。  
- 若厂商要求不同摘要算法，请按厂商文档调整 `DTS_PKI_DIGEST`。  

## 相关代码位置
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/pki/PkiVerificationService.java`  
  - `resolveGatewayPort(...)`：`signType` → 端口路由  
  - `verifyWithVendor(...)` / `verifyViaHttpGateway(...)`：验签方式  
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/config/PkiAuthProperties.java`  
  - `dts.pki.*` 配置项定义  
