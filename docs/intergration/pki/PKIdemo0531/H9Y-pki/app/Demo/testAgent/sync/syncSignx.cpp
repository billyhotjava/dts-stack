#include "syncSignx.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
/* SyncSignX */
/**
 * @description:  获取签名数据，用于测试
 * @param {null}
 * @return: std::string
 */
std::string &SyncSignX::syncGetSignData() { return mSignData; }

/**
 * @description: 获取签名数据，用于测试
 * @param {null}
 * @return: std::string
 */
std::string &SyncSignX::syncGetP7SignData() { return mP7SignData; }

/**
 * @description: 获取组p7数字信封数据，用于测试
 * @param {null}
 * @return: std::string
 */
std::string &SyncSignX::syncGetEnvelopeEncryptData() { return mEnvelopeEncrypt; }

/**
 * @description: 数据签名
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 待签名数据，base64编码
 * @param {isBase64SrcData} 是否为base64编码源数据，1表示是，0表示否
 * @param {type} 签名类型,1表示PM-BD签名,2表示SM2/RSA签名,3 SSL建链定制签名
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncSignData(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData,
                            const uint32 &isBase64SrcData, const std::string &type) {
    printf("============================================== signData\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_SIGNDATA;

    std::string json = buildSignDataReq(devId, appName, conName, srcData, isBase64SrcData, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);

    /// 获取签名结果
    parseSignDatResp(resp.data->getDataString(), mSignData);
    return resp.errCode;
}

/**
 * @description: 验证签名
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 签名源数据，base64编码
 * @param {signData} 签名数据，base64编码
 * @param {isBase64SrcData} 是否为base64编码源数据，1表示是，0表示否
 * @param {type} 签名类型,1表示PM-BD验签,2表示SM2/RSA验签
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncVerifySignData(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData,
                                  const std::string &signData, const uint32 &isBase64SrcData, const uint32 &type) {
    printf("============================================== verifySignData\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_VERIFYSIGN;

    std::string json = buildVerifyDataReq(devId, appName, conName, srcData, signData, isBase64SrcData, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: p7签名
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 待签名数据(base64编码)
 * @param {mdType} 0表示PKCS7_DETACHED
 * @param {attachData} 指定的摘要类型,"1"-MD5 "2"-SHA1 "3"-SM3 "4"-SHA256
 * @param {signwithSM2Std} 用于sm2签名，1表示使用SM2规范，0表示使用RFC规范，默认0
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncSignMessage(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData,
                               const uint32 &mdType, const std::string &attachData, const uint32 &signwithSM2Std, const uint32 &noAttr) {
    printf("============================================== signMessage\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_SIGNP7;

    std::string json = buildPkcs7SignReq(devId, appName, conName, srcData, mdType, attachData, signwithSM2Std, noAttr);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);

    /// 获取签名结果
    parsePkcs7SignResp(resp.data->getDataString(), mP7SignData);

    return resp.errCode;
}

/**
 * @description: p7验证签名
 * @param {srcData} 签名数据的源数据(base64)
 * @param {signData} 签名数据(base64)
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncVerifyMessage(const std::string &srcData, const std::string &signData) {
    printf("============================================== verifyMessage\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_VERIFYSIGNP7;

    std::string json = buildPkcs7VerifyReq(srcData, signData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 外来公钥验签
 * @param {devId} 设备ID
 * @param {pubkey} 公钥
 * @param {srcData} 签名数据的源数据(base64编码)
 * @param {signData} 签名数据(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncExtECCVerify(const std::string &devId, const std::string &pubkey, const std::string &srcData, const std::string &signData) {
    printf("============================================== extECCVerify\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_EXTECCPUBVERIFY;

    std::string json = buildExPubVerifyReq(devId, pubkey, srcData, signData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 外来证书验签
 * @param {devId} 设备ID
 * @param {b64cert} 证书内容(base64)
 * @param {srcData} 签名数据的源数据(base64编码)
 * @param {signData} 签名数据(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncExtECCVerifyEx(const std::string &devId, const std::string &b64cert, const std::string &srcData, const std::string &signData) {
    printf("============================================== extECCVerifyEx\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_EXTECCCERTVERIFY;

    std::string json = buildExCertVerifyReq(devId, b64cert, srcData, signData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 生成证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {signFlag} 1表示签名证书,0表示加密证书
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncDupCertWithTemplate(const std::string &devId, const std::string &appName, const std::string &conName,
                                       const std::string &signFlag) {
    printf("============================================== dupCertWithTemplate\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_MKCERTFROMTEMP;

    std::string json = buildDupb64certWithTemplateReq(devId, appName, conName, signFlag);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 解析证书
 * @param {cert} 证书内容(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncParseCert(const std::string cert) {
    printf("============================================== parseCert\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_PARSECERT;

    std::string json = buildCertParseReq(cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 组p7数字信封
 * @param {srcData} 待签名数据(base64编码)
 * @param {cert} 证书
 * @param {cihperType} 0"-DES "1"-3DES "2"-AES "3"-SM4
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncEnvelopeEncrypt(const std::string &srcData, const std::string &cert, const uint32 &cihperType) {
    printf("============================================== envelopeEncrypt\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_ENVELOPEENC;

    std::string json = buildEnvelopeEncryptReq(srcData, cert, cihperType);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);

    parseEnvelopeEncryptResp(resp.data->getDataString(), mEnvelopeEncrypt);
    return resp.errCode;
}

/**
 * @description: 解p7数字信封
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 密文数据
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncEnvelopeDecrypt(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData) {
    printf("============================================== envelopeDecrypt\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_ENVELOPEDEC;

    std::string json = buildEnvelopeDecryptReq(devId, appName, conName, srcData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: p7外部证书验签
 * @param {srcData} 签名数据的源数据(base64)
 * @param {signData} 签名数据(base64)
 * @param {cert} 证书
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncVerifySignedMessage(const std::string &srcData, const std::string &signData, const std::string &cert) {
    printf("============================================== verifySignedMessage\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_EXTECCCERTVERIFYP7;

    std::string json = buildVerifySignedMessageReq(srcData, signData, cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取证书扩展项
 * @param {cert} 证书
 * @param {oid} oid
 * @return: 0：成功, 其他：失败
 */
int SyncSignX::syncGetExtension(const std::string &cert, const std::string &oid) {
    printf("============================================== GetExtension\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_GETEXTENSION;

    std::string json = buildGetExtensionReq(cert, oid);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(SIGNXSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}
}  // namespace testAgent
}  // namespace koal