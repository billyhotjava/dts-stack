# 2025-11-17 PKI 普密网关验签支持

## 变更概览
- dts-admin PKI 验签新增“HTTP 网关”路径（普密/无厂商 JAR 时使用），兼容原有商密 JAR 模式。
- `/api/keycloak/auth/pki-login` 支持将 `signType` 传入，按 SM2/RSA 走主端口（默认 5000），其他走备用端口（默认 10009）。
- 新配置：
  - `DTS_PKI_GATEWAY_ALT_PORT`（默认 10009）
  - `DTS_PKI_GATEWAY_ENDPOINT`（默认 `/wglogin`）
  - 已有 `DTS_PKI_GATEWAY_HOST`、`DTS_PKI_GATEWAY_PORT`、`DTS_PKI_API_BASE` 可复用。

## 验证方式
1) 生成 `.env` 并重建 dts-admin。确保 8081 暴露（内部调用）。
2) 配置示例（普密 HTTP 网关）：
   ```env
   DTS_PKI_ENABLED=true
   DTS_PKI_MODE=gateway
   DTS_PKI_GATEWAY_HOST=192.168.160.150
   DTS_PKI_GATEWAY_PORT=5000       # SM2/RSA
   DTS_PKI_GATEWAY_ALT_PORT=10009  # 其他算法
   DTS_PKI_GATEWAY_ENDPOINT=/wglogin
   ```
   或直接设置 `DTS_PKI_API_BASE=http://192.168.160.150:5000`。
3) 调用 `/api/keycloak/auth/pki-login` 时传入：
   - `originDataB64`（签名原文的 Base64）
   - `signDataB64`（签名 Base64）
   - `certContentB64`（证书 Base64，可选，用于返回 subject/issuer/序列号）
   - `signType`（例如 SM2 / RSA，决定使用主端口或备用端口）
4) 后端将表单 POST 至网关 `${gatewayHost}:${port}${gatewayEndpoint}`，响应包含“成功”/“success”即视为验签通过。

## 注意事项
- 若配置了 `vendor-jar-path` 仍优先走商密 JAR 验签；未配置时才会走 HTTP 网关。
- 证书解析失败不阻塞登录，但无法从证书提取 UID/CN 将导致账号映射失败。
- 请在网关侧确认 `/wglogin` 接口参数名：`signData`、`originData`、`certContent`、`gwIP`、`gwPort`。
