
#include "asyncSignx.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
/* signX */
/**
 * @description:  获取签名数据，用于测试
 * @param {null}
 * @return: std::string
 */
std::string &AsyncSignX::asyncGetSignData() { return mSignData; }

/**
 * @description:  设置签名数据，用于测试
 * @param {null}
 * @return: true: 成功， false：失败
 */
bool AsyncSignX::asyncSetSignData(std::string data) {
    if (data.empty()) {
        return false;
    }
    mSignData = data;
    return true;
}

/**
 * @description: 获取签名数据，用于测试
 * @param {null}
 * @return: std::string
 */
std::string &AsyncSignX::asyncGetP7SignData() { return mP7SignData; }

/**
 * @description:  设置签名数据，用于测试
 * @param {null}
 * @return: true: 成功， false：失败
 */
bool AsyncSignX::asyncSetP7SignData(std::string data) {
    if (data.empty()) {
        return false;
    }
    mP7SignData = data;
    return true;
}

/**
 * @description: 获取组p7数字信封数据，用于测试
 * @param {null}
 * @return: std::string
 */
std::string &AsyncSignX::asyncGetEnvelopeEncryptData() { return mEnvelopeEncrypt; }

/**
 * @description:  设置组p7数字信封数据，用于测试
 * @param {null}
 * @return: true: 成功， false：失败
 */
bool AsyncSignX::asyncSetEnvelopeEncryptData(std::string data) {
    if (data.empty()) {
        return false;
    }
    mEnvelopeEncrypt = data;
    return true;
}

/**
 * @description: 数据签名
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 待签名数据，base64加密
 * @param {isBase64SrcData} 是否为base64编码源数据，1表示是，0表示否
 * @param {type} 签名类型,1表示PM-BD签名,2表示SM2/RSA签名,3 SSL建链定制签名
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncSignData(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData,
                              const uint32 &isBase64SrcData, const std::string &type) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_SIGNDATA;

    std::string json = buildSignDataReq(devId, appName, conName, srcData, isBase64SrcData, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, this);
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
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncVerifySignData(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData,
                                    const std::string &signData, const uint32 &isBase64SrcData, const uint32 &type) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_VERIFYSIGN;

    std::string json = buildVerifyDataReq(devId, appName, conName, srcData, signData, isBase64SrcData, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
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
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncSignMessage(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData,
                                 const uint32 &mdType, const std::string &attachData, const uint32 &signwithSM2Std, const uint32 &noAttr) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_SIGNP7;

    std::string json = buildPkcs7SignReq(devId, appName, conName, srcData, mdType, attachData, signwithSM2Std, noAttr);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, this);
}

/**
 * @description: p7验证签名
 * @param {srcData} 签名数据的源数据(base64)
 * @param {signData} 签名数据(base64)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncVerifyMessage(const std::string &srcData, const std::string &signData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_VERIFYSIGNP7;

    std::string json = buildPkcs7VerifyReq(srcData, signData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: 外来公钥验签
 * @param {devId} 设备ID
 * @param {pubkey} 公钥
 * @param {srcData} 签名数据的源数据(base64编码)
 * @param {signData} 签名数据(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncExtECCVerify(const std::string &devId, const std::string &pubkey, const std::string &srcData, const std::string &signData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_EXTECCPUBVERIFY;

    std::string json = buildExPubVerifyReq(devId, pubkey, srcData, signData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: 外来证书验签
 * @param {devId} 设备ID
 * @param {b64cert} 证书内容(base64)
 * @param {srcData} 签名数据的源数据(base64编码)
 * @param {signData} 签名数据(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncExtECCVerifyEx(const std::string &devId, const std::string &b64cert, const std::string &srcData, const std::string &signData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_EXTECCCERTVERIFY;

    std::string json = buildExCertVerifyReq(devId, b64cert, srcData, signData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: 生成证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {signFlag} 1表示签名证书,0表示加密证书
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncDupCertWithTemplate(const std::string &devId, const std::string &appName, const std::string &conName,
                                         const std::string &signFlag) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_MKCERTFROMTEMP;

    std::string json = buildDupb64certWithTemplateReq(devId, appName, conName, signFlag);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: 解析证书
 * @param {cert} 证书内容(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncParseCert(const std::string cert) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_PARSECERT;

    std::string json = buildCertParseReq(cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: 组p7数字信封
 * @param {srcData} 待签名数据(base64编码)
 * @param {cert} 证书
 * @param {cihperType} 0"-DES "1"-3DES "2"-AES "3"-SM4
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncEnvelopeEncrypt(const std::string &srcData, const std::string &cert, const uint32 &cihperType) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_ENVELOPEENC;

    std::string json = buildEnvelopeEncryptReq(srcData, cert, cihperType);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, this);
}

/**
 * @description: 解p7数字信封
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 密文数据
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncEnvelopeDecrypt(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &srcData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_ENVELOPEDEC;

    std::string json = buildEnvelopeDecryptReq(devId, appName, conName, srcData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: p7外部证书验签
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {srcData} 签名数据的源数据(base64)
 * @param {signData} 签名数据(base64)
 * @param {cert} 证书
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncVerifySignedMessage(const std::string &srcData, const std::string &signData, const std::string &cert) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_EXTECCCERTVERIFYP7;

    std::string json = buildVerifySignedMessageReq(srcData, signData, cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(SIGNXSERVICE, &req, NULL);
}

/**
 * @description: 获取证书扩展项
 * @param {cert} 证书
 * @param {oid} oid
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncSignX::asyncGetExtension(const std::string &cert, const std::string &oid) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_SIGNX_GETEXTENSION;

    std::string json = buildGetExtensionReq(cert, oid);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(SIGNXSERVICE, &req, NULL);
}
}  // namespace testAgent
}  // namespace koal