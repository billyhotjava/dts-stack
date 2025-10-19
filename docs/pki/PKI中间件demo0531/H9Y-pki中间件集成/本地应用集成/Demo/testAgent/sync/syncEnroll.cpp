#include "syncEnroll.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
/* SyncEnRoll */

/**
 * @description: 创建P10请求
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {dn} DN项
 * @param {extensionType} 临时密钥扩展项，1.不携带，2.携带临时密钥，其他值.协同模式
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncMakePkcs10(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &dn,
                               const int &extensionType, const int &reqDigst) {
    printf("============================================== makePkcs10\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_MKP10;

    std::string json = buildMakePkcs10Req(devId, appName, conName, dn, extensionType, reqDigst);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 生成密钥对
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {keyType} 0:SM2,1:RSA
 * @param {keyLen} KeyType为rsa时有效
 * @param {purpose} keyType为SM2时候有效（目前key不支持2）,1. SGD_SM2_1,2. SGD_SM2_3
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncGenKeypair(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &keyType,
                               const std::string &keyLen, const uint32 &purpose) {
    printf("============================================== genKeypair\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_KEYPAIR;

    std::string json = buildGenb64KeypairReq(devId, appName, conName, keyType, keyLen, purpose);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导入密钥对
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {b64Key} 密钥对
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncImportEncKeypair(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &b64Key) {
    printf("============================================== importEncKeypair\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_IMPORTKEYPAIR;

    std::string json = buildImportEncReq(devId, appName, conName, b64Key);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导入证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {tyb64certpe} 证书(base64编码)
 * @param {purpose} 1表示签名证书,0表示加密证书
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncImportX509Cert(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &b64cert,
                                   const std::string &purpose) {
    printf("============================================== importX509Cert\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_IMPORTX509;

    std::string json = buildInstallCertReq(devId, appName, conName, b64cert, purpose);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导入pfx证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {b64cert} pfx证书(base64编码)
 * @param {certPass} 密码
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncImportPfxCert(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &b64cert,
                                  const std::string &certPass) {
    printf("============================================== importPfxCert\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_IMPORTPFX;

    std::string json = buildImportPfxReq(devId, appName, conName, b64cert, certPass);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导出证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {certType} 1表示签名证书, 0表示加密证书
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncGetCert(const std::string &devId, const std::string &appName, const std::string &conName, const std::string &certType) {
    printf("============================================== getCert\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_GETCERT;

    std::string json = buildGetb64certReq(devId, appName, conName, certType);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导入pfx证书到skffile
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {conName} 容器名称
 * @param {b64cert} pfx证书(base64编码)
 * @param {certPass} 密码
 * @return: 0：成功, 其他：失败
 */
int SyncEnRoll::syncImportPfx2SkfFile(const std::string &devId, const std::string &appName, const std::string &conName, unsigned int signFlag,
                                      const std::string &certPass, const std::string &b64cert) {
    printf("============================================== importPfx2SkfFile\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_ENROLL_IMPORTPFX2SKFILE;

    std::string json = buildImportPfx2SkfFileReq(devId, appName, conName, signFlag, certPass, b64cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(ENROLLSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}
}  // namespace testAgent
}  // namespace koal