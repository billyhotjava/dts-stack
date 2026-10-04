# Koal PKI Thrift 接口对照表（前端 vs 厂商 Demo）

本文用于和厂商对齐 Koal PKI 中间件的 Thrift Multiplexer 服务名、接口方法、`msgType` 及关键入参。

参考厂商 Demo：
- `docs/intergration/pki/PKIdemo0531/H9Y-pki/web/Demo/thriftclient_js/thriftclient_js0531/signClient.html`

对应前端实现：
- `source/dts-platform-webapp/src/api/services/koalPkiClient.ts`

## 1) Multiplexer 服务名对照

| 功能模块 | 厂商 Demo 变量 | Multiplexer serviceName | 前端实现 |
|---|---|---|---|
| 会话/登录 | `KOAL_SERVER_NAME` | `pkiService` | `createClient("pkiService", ...)` |
| 设备/证书管理 | `DEV_SERVER_NAME` | `deviceOperator` | `createClient("deviceOperator", ...)` |
| 签名/证书解析 | `SIGNXDLL_SERVER_NAME` | `signxPlugin` | `createClient("signxPlugin", ...)` |
| 证书获取（Enroll） | `ENROLL_SERVER_NAME` | `enrollPlugin` | `createClient("enrollPlugin", ...)`（并兼容回退 `enRollService`） |

> 注意：厂商 Demo 使用 `enrollPlugin`，某些中间件版本可能也暴露 `enRollService`。前端实现会优先 `enrollPlugin`，失败后回退 `enRollService`。

## 2) 核心接口对照（method / msgType / 入参）

| 场景 | Thrift client | method | msgType | msgRequest.jsonBody 关键字段 | 说明 |
|---|---|---:|---:|---|---|
| 登录 | `pkiService` | `login` | `0x01` | `appName`, `appID`, `token` | Demo/前端均使用固定 app 参数（需联系厂商分配正式值） |
| 登出 | `pkiService` | `logout` | N/A | N/A | 仅传 `sessionTicket(sessionID,ticket)` |
| 枚举证书 | `deviceOperator` | `getAllCert` | `0x28` | 无/空对象 | 返回 `payload.certs`（证书列表） |
| 验证 PIN | `deviceOperator` | `verifyPIN` | `0x18` | `devID`, `appName`, `PINType`, `PIN` | `PINType` Demo 常用 `"1"` |
| 数据签名 | `signxPlugin` | `signData` | `0x10` | `devID`, `appName`, `conName`, `srcData`, `isBase64SrcData`, `type`, `mdType` | `srcData` 为 base64；`type/mdType` 见下表 |
| 解析证书 | `signxPlugin` | `parseCert` | `0x17` | `cert` | `cert` 为证书 base64；用于解析算法信息（RSA/SM2/PM 等） |
| 获取证书内容 | `enrollPlugin` | `getCert` | `0x25` | `devID`, `appName`, `conName`, `certType` | `certType`: `"1"`=签名证书，`"0"`=加密证书；响应可能是 `cert/b64cert/p7cert/certificate` |
| 导出证书内容 | `deviceOperator` | `exportCertificate` | `0x22` | `devID`, `appName`, `containerName`, `signFlag` | `signFlag`: `"1"`=签名证书，`"0"`=加密证书；响应 `obj.cert` |

## 3) signData 的 type / mdType 枚举（与 Demo 对齐）

摘自 `signClient.html` 注释：
- `type`（签名类型）
  - `1`：PM-BD 签名
  - `2`：SM2/RSA 签名
  - `3`：SSL 建链定制签名
  - `4`：银行二代 K 签名
- `mdType`（摘要类型）
  - `1`：MD5
  - `2`：SHA1
  - `3`：SM3
  - `4`：SHA256

当前前端策略（用于登录 nonce 签名）：
- 优先尝试 `type=2`（SM2/RSA）
  - 若识别为 SM2：`mdType=3(SM3)`
  - 若识别为 RSA：`mdType=2(SHA1)`
- 若证书类型/场景判定为 PM（例如 `certType=other/othere`，或 issuer CN 包含 `ZWYCA`）则使用：
  - `type=1(PM-BD)` + `mdType=4(SHA256)`
  - `certContent` 使用 `dupCertWithTemplate(msgType=0x16)` 生成的 `obj.cert`

## 4) 需要厂商确认的字段（建议沟通点）

1. `parseCert(msgType=0x17)` 的返回 `jsonBody` 中，哪个字段能可靠标识算法（RSA/SM2/PM）？（字段名 + 示例）
2. RSA 登录签名建议的 `mdType`：Demo 支持 `SHA1(2)` / `SHA256(4)`，现场网关验签要求是哪一个？
3. “普密证书”在证书列表中 `certType=other/othere` 的含义：是否等价于 PM-BD 流程？是否必须使用特定 `type/mdType`？

## 附：makePkcs10 的 digestType / reqDigst（Demo 片段解读）

`signClient.html` 里的 `enRollService.makePkcs10(msgType=0x20)` 会用到：
- `digestType`：公钥摘要获取类型（1=MD5，2=SHA1，3=SM3，4=SHA256）
- `reqDigst`：证书请求签名摘要类型（1=MD5，2=SHA1，3=SM3，4=SHA256）
  - Demo 注释明确：`reqDigst` 其他值或不写时默认 **SM2→SM3，RSA→SHA256**

注意：`makePkcs10` 是 CSR（证书请求）流程用的参数，和登录时调用的 `signxPlugin.signData(msgType=0x10)` 的 `mdType` 不是同一个接口参数；但它体现了厂商对 RSA 推荐使用 `SHA256` 的默认倾向，建议厂商明确登录签名应选 `SHA1` 还是 `SHA256`。
