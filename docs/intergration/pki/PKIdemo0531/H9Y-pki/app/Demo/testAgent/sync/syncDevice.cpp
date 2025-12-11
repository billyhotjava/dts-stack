#include "syncDevice.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
/**
 * @description: 获取设备ID
 * @param {null}
 * @return: 设备ID
 */
std::string SyncDevice::syncGetDevID() {
    if (mDevID.empty()) {
        syncGetDevices();
    }
    return mDevID;
}

/**
 * @description: 获取设备type
 * @param {null}
 * @return: 设备type
 */
std::string SyncDevice::syncGetDevType() {
    if (mDevType.empty()) {
        syncGetDevices();
    }
    return mDevType;
}

/**
 * @description: 获取设备列表
 * @param {null}
 * @return: 0, 其他：失败
 */
int SyncDevice::syncGetDevices() {
    printf("=========================================================== getDevices\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETDEVICES;

    reqSync(DEVSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);

    std::vector<std::map<std::string, std::string> > arrayDevID;
    parseGetDevicesResponse(resp.data->getDataString(), arrayDevID);
    //提取一个devID
    std::vector<std::map<std::string, std::string> >::iterator it = arrayDevID.begin();
    if (it == arrayDevID.end()) {
        printf("without any SyncDevice, process exit\n");
        return -1;
    }
    mDevID = (*it)["devID"];
    mDevType = (*it)["devType"];
    printf("devID=%s\n", mDevID.c_str());
    printf("devType=%s\n", mDevType.c_str());
    return resp.errCode;
}

/**
 * @description: 获取设备信息
 * @param {devId} 设备ID
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetDevInfo(const std::string &devId) {
    printf("=========================================================== getDevInfo\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETDEVINFO;

    std::string json = buildGetDevInfoReq(devId);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 设置设备标签
 * @param {devId} 设备ID
 * @param {lable} 设备标签
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncSetDevLable(const std::string &devId, const std::string &lable) {
    printf("=========================================================== setDevLable\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_SETDEVLABLE;

    std::string json = buildSetDevLableReq(devId, lable);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);

    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 设备命令传输
 * @param {devId} 设备ID
 * @param {command} 用于填写与用户约定的命令号
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncTransMitData(const std::string &devId, const std::string &command) {
    printf("=========================================================== transMitData\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_TRANSMITDATA;

    std::string json = buildTransMitDataReq(devId, command);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 设备认证
 * @param {devId} 设备ID
 * @param {authData} 认证数据(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncDevAuth(const std::string &devId, const std::string &authData) {
    printf("============================================== devAuth\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DEVAUTH;

    std::string json = buildDevAuthReq(devId, authData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 修改设备认证秘钥
 * @param {type} 设备ID
 * @param {type} 认证数据(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncChangeAuthKey(const std::string &devId, const std::string &authData) {
    printf("============================================== changeAuthKey\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CHANGEAUTHKEY;

    std::string json = buildChangeAuthKeyReq(devId, authData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetPINInfo(const std::string &devId, const std::string &appName, const uint32 &PINType) {
    printf("============================================== getPINInfo\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETPININFO;

    std::string json = buildGetPINInfoReq(devId, appName, PINType);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 修改PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @param {oldPIN} 旧PIN
 * @param {newPIN} 新PIN
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncChangePIN(const std::string &devId, const std::string &appName, const uint32 &PINType, const std::string &oldPIN,
                              const std::string &newPIN) {
    printf("============================================== changePIN\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CHANGEPIN;

    std::string json = buildChangePINReq(devId, appName, PINType, oldPIN, newPIN);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 校验PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @param {PIN} PIN码
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncVerifyPIN(const std::string &devId, const std::string &appName, const uint32 &PINType, const std::string &PIN) {
    printf("============================================== verifyPIN\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_VERIFYPIN;

    std::string json = buildVerifyPINReq(devId, appName, PINType, PIN);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取缓存PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @param {cachedPIN} PIN缓存
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetCachedPIN(const std::string &devId, const std::string &appName, const uint32 &PINType) {
    printf("============================================== GetCachedPIN\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETCACHEDPIN;

    std::string json = buildGetCachedPINReq(devId, appName, PINType);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 解锁PIN码
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {adminPIN} 管理员PIN
 * @param {userPIN} 用户PIN
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncUnlockPIN(const std::string &devId, const std::string &appName, const std::string &adminPIN, const std::string &userPIN) {
    printf("============================================== unlockPIN\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_UNLOCKPIN;

    std::string json = buildUnlockPINReq(devId, appName, adminPIN, userPIN);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取应用列表
 * @param {devId} 设备ID
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetAppList(const std::string &devId) {
    printf("============================================== getAppList\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETAPPLIST;

    std::string json = buildGetAppListReq(devId);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 创建应用
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {admin_PIN} 管理员PIN
 * @param {admin_maxRetryCount} 最大重试次数
 * @param {user_PIN} 用户PIN
 * @param {user_maxRetryCount} 最大重试次数
 * @param {fileRight} 创建文件和容器的权限
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncCreateApp(const std::string &devId, const std::string &appName, const std::string &admin_PIN, const uint32 &admin_maxRetryCount,
                              const std::string &user_PIN, const uint32 &user_maxRetryCount, const uint32 &fileRight) {
    printf("============================================== createApp\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CREATEAPP;

    std::string json = buildCreateAppReq(devId, appName, admin_PIN, admin_maxRetryCount, user_PIN, user_maxRetryCount, fileRight);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 删除应用
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncDelApp(const std::string &devId, const std::string &appName) {
    printf("============================================== delApp\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DELAPP;

    std::string json = buildDelAppReq(devId, appName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取容器列表
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetContainers(const std::string &devId, const std::string &appName) {
    printf("============================================== getContainers\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETCONTAINERS;

    std::string json = buildGetContainersReq(devId, appName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 创建容器
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncCreateContainer(const std::string &devId, const std::string &appName, const std::string &containerName) {
    printf("============================================== createContainer\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CREATECONTAINER;

    std::string json = buildCreateContainerReq(devId, appName, containerName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 删除容器
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncDelContainer(const std::string &devId, const std::string &appName, const std::string &containerName) {
    printf("============================================== delContainer\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DELCONTAINER;

    std::string json = buildDelContainerReq(devId, appName, containerName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取容器类型
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetContainerType(const std::string &devId, const std::string &appName, const std::string &containerName) {
    printf("============================================== getContainerType\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETCONTAINERTYPE;

    std::string json = buildGetContainerTypeReq(devId, appName, containerName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导入数字证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @param {signFlag} 1表示签名证书，0表示加密证书
 * @param {cert} 证书内容(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncImportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName, const uint32 &signFlag,
                                      const std::string &cert) {
    printf("============================================== importCertificate\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_IMPORTCERTIFICATE;

    std::string json = buildImportCertificateReq(devId, appName, containerName, signFlag, cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导出数字证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @param {signFlag} 1表示签名证书，0表示加密证书
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncExportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName,
                                      const uint32 &signFlag) {
    printf("============================================== exportCertificate\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXPORTCERTIFICATE;

    std::string json = buildExportCertificatReq(devId, appName, containerName, signFlag);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取证书列表
 * @param {NULL}
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetAllCert() {
    printf("============================================== GetAllCert\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETALLCERT;

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 通过证书序列号获取证书列表
 * @param {NULL}
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetAllCertBySN() {
    printf("============================================== GetAllCertBySN\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETALLCERT;

    // std::string json = "{\"includeSN\":[\"20250000000000000000001B\",\"20250000000000000000001C\"]}";
    std::string json = "{\"includeSN\":[\"20250000000000000000001B\"]}";

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 导出公钥
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @param {signFlag} 1表示签名公钥，0表示加密公钥
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncExportPublicKey(const std::string &devId, const std::string &appName, const std::string &containerName, const uint32 &signFlag) {
    printf("============================================== exportPublicKey\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXPORTPUBLICKEY;

    std::string json = buildExportPublicKeyReq(devId, appName, containerName, signFlag);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 外来公钥加密
 * @param {devId} 设备ID
 * @param {pubKey} 公钥(base64编码)
 * @param {type} 1表示RSA,2表示ECC
 * @param {srcData} 源数据(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncExtPubKeyEncrypt(const std::string &devId, const std::string &pubKey, const uint32 &type, const std::string &srcData) {
    printf("============================================== extPubKeyEncrypt\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXTPUBKEYENCRYPT;

    std::string json = buildExtPubKeyEncryptReq(devId, pubKey, type, srcData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 外来私钥解密
 * @param {devId} 设备ID
 * @param {priKey} 私钥(base64编码)
 * @param {type} 1表示RSA,2表示ECC
 * @param {encryptData} 密文数据(base64编码)
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncExtPriKeyDecrypt(const std::string &devId, const std::string &priKey, const uint32 &type, const std::string &encryptData) {
    printf("============================================== extPriKeyDecrypt\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXTPRIKEYDECRYPT;

    std::string json = buildExtPriKeyDecryptReq(devId, priKey, type, encryptData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取provider列表
 * @param {NULL}
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetProviders() {
    printf("============================================== getProviders\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETPROVIDERS;

    reqSync(DEVSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 设置provider
 * @param {name} provider name
 * @param {VPID} provider VPID, like ["055C_F603","055C_F604"]
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncSetProvider(const std::string &name, const std::string &VPID) {
    printf("============================================== setProvider\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_SETPROVIDER;

    std::string json = buildSetProviderReq(name, VPID);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description:解锁指纹
 * @param {devId} 设备id
 * @param {appName} 应用名称
 * @param {type} 指纹类型
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncUnblockFinger(const std::string &devId, const std::string &appName, const unsigned int &type) {
    printf("============================================== syncUnblockFinger\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_UNBLOCKFINGER;

    std::string json = buildUnblockFingerReq(devId, appName, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 指纹初始化
 * @param {devId} 设备id
 * @param {type} 指纹类型
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncInitFinger(const std::string &devId, const unsigned int &type) {
    printf("============================================== syncInitFinger\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_INITFINGER;

    std::string json = buildInitFingerReq(devId, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 是否存在指纹
 * @param {devId} 设备id
 * @param {appName} 应用名称
 * @param {type} 指纹类型
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncHasFinger(const std::string &devId, const std::string &appName, const unsigned int &type) {
    printf("============================================== syncHasFinger\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_HASFINGER;

    std::string json = buildHasFingerReq(devId, appName, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 验证指纹
 * @param {devId} 设备id
 * @param {appName} 应用名称
 * @param {type} 指纹类型
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncVerifyFinger(const std::string &devId, const std::string &appName, const unsigned int &type) {
    printf("============================================== syncVerifyFinger\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_VERIFYFINGER;

    std::string json = buildVerifyFingerReq(devId, appName, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 取消指纹
 * @param {devId} 设备id
 * @param {type} 指纹类型
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncCancleFinger(const std::string &devId) {
    printf("============================================== syncCancleFinger\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CANCLEFINGER;

    std::string json = buildCancleFingerReq(devId);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 创建文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @param {fileSize} 文件大小
 * @param {readRights} 读权限
 * @param {writeRights} 写权限
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncCreateFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &fileSize,
                               const unsigned int &readRights, const unsigned int &writeRights) {
    printf("============================================== syncCreateFile\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CERATEFILE;

    std::string json = buildCreateFileReq(devId, appName, fileName, fileSize, readRights, writeRights);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 删除文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncDeleteFile(const std::string &devId, const std::string &appName, const std::string &fileName) {
    printf("============================================== syncDeleteFile\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DELETEFILE;

    std::string json = buildDeleteFileReq(devId, appName, fileName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取文件列表
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetFileList(const std::string &devId, const std::string &appName) {
    printf("============================================== syncGetFileList\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETFILELIST;

    std::string json = buildGetFileListReq(devId, appName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 获取文件属性
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGetFileInfo(const std::string &devId, const std::string &appName, const std::string &fileName) {
    printf("============================================== syncGetFileInfo\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETFILEINFO;

    std::string json = buildGetFileInfoReq(devId, appName, fileName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 读文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @param {offset} 文件读取偏移位置
 * @param {size} 读取的长度
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncReadFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &offset,
                             const unsigned int &size) {
    printf("============================================== syncReadFile\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_READFILE;

    std::string json = buildReadFileReq(devId, appName, fileName, offset, size);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 写文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @param {offset} 文件读取偏移位置
 * @param {data} 写入的数据
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncWriteFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &offset,
                              const std::string &data) {
    printf("============================================== syncWriteFile\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_WRITEFILE;

    std::string json = buildWriteFileReq(devId, appName, fileName, offset, data);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

/**
 * @description: 生成随机数
 * @param {devId} 设备ID
 * @param {randomLen} 随机数字节数
 * @return: 0：成功, 其他：失败
 */
int SyncDevice::syncGenRandom(const std::string &devId, const unsigned int &randomLen) {
    printf("============================================== GenRandom\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GENRANDOM;

    std::string json = buildGenRandomReq(devId, randomLen);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(DEVSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}
}  // namespace testAgent
}  // namespace koal