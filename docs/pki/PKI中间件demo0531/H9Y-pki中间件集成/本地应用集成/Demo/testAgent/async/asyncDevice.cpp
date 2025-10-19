#include "asyncDevice.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
/**
 * @description: 获取设备ID
 * @param {null}
 * @return: 设备ID
 */
std::string AsyncDevice::asyncGetDevID() { return mDevID; }

/**
 * @description: 设置设备ID
 * @param {devId} 设备ID
 * @return: true：成功，false：失败
 */
bool AsyncDevice::asyncSetDevID(std::string devId) {
    if (devId.empty()) {
        return false;
    }
    mDevID = devId;
    return true;
}

/**
 * @description: 获取设备列表
 * @param {null}
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetDevices() {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETDEVICES;
    return reqAsync(DEVSERVICE, &req, this);
}

/**
 * @description: 获取设备信息
 * @param {devId} 设备ID
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetDevInfo(const std::string &devId) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETDEVINFO;

    std::string json = buildGetDevInfoReq(devId);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 设置设备标签
 * @param {devId} 设备ID
 * @param {lable} 设备标签
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncSetDevLable(const std::string &devId, const std::string &lable) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_SETDEVLABLE;

    std::string json = buildSetDevLableReq(devId, lable);
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 设备命令传输
 * @param {devId} 设备ID
 * @param {command} 用于填写与用户约定的命令号
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncTransMitData(const std::string &devId, const std::string &command) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_TRANSMITDATA;

    std::string json = buildTransMitDataReq(devId, command);
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 设备认证
 * @param {devId} 设备ID
 * @param {authData} 认证数据(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncDevAuth(const std::string &devId, const std::string &authData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DEVAUTH;

    std::string json = buildDevAuthReq(devId, authData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 修改设备认证秘钥
 * @param {type} 设备ID
 * @param {type} 认证数据(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncChangeAuthKey(const std::string &devId, const std::string &authData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CHANGEAUTHKEY;

    std::string json = buildChangeAuthKeyReq(devId, authData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetPINInfo(const std::string &devId, const std::string &appName, const uint32 &PINType) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETPININFO;

    std::string json = buildGetPINInfoReq(devId, appName, PINType);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 修改PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @param {oldPIN} 旧PIN
 * @param {newPIN} 新PIN
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncChangePIN(const std::string &devId, const std::string &appName, const uint32 &PINType, const std::string &oldPIN,
                                const std::string &newPIN) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CHANGEPIN;

    std::string json = buildChangePINReq(devId, appName, PINType, oldPIN, newPIN);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 校验PIN
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {PINType} PIN类型。0为ADMIN_TYPE，1为USER_TYPE
 * @param {PIN} PIN码
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncVerifyPIN(const std::string &devId, const std::string &appName, const uint32 &PINType, const std::string &PIN) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_VERIFYPIN;

    std::string json = buildVerifyPINReq(devId, appName, PINType, PIN);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 解锁PIN码
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {adminPIN} 管理员PIN
 * @param {userPIN} 用户PIN
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncUnlockPIN(const std::string &devId, const std::string &appName, const std::string &adminPIN, const std::string &userPIN) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_UNLOCKPIN;

    std::string json = buildUnlockPINReq(devId, appName, adminPIN, userPIN);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取应用列表
 * @param {devId} 设备ID
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetAppList(const std::string &devId) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETAPPLIST;

    std::string json = buildGetAppListReq(devId);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
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
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncCreateApp(const std::string &devId, const std::string &appName, const std::string &admin_PIN, const uint32 &admin_maxRetryCount,
                                const std::string &user_PIN, const uint32 &user_maxRetryCount, const uint32 &fileRight) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CREATEAPP;

    std::string json = buildCreateAppReq(devId, appName, admin_PIN, admin_maxRetryCount, user_PIN, user_maxRetryCount, fileRight);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 删除应用
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncDelApp(const std::string &devId, const std::string &appName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DELAPP;

    std::string json = buildDelAppReq(devId, appName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取容器列表
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetContainers(const std::string &devId, const std::string &appName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETCONTAINERS;

    std::string json = buildGetContainersReq(devId, appName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 创建容器
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncCreateContainer(const std::string &devId, const std::string &appName, const std::string &containerName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CREATECONTAINER;

    std::string json = buildCreateContainerReq(devId, appName, containerName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 删除容器
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncDelContainer(const std::string &devId, const std::string &appName, const std::string &containerName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DELCONTAINER;

    std::string json = buildDelContainerReq(devId, appName, containerName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取容器类型
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetContainerType(const std::string &devId, const std::string &appName, const std::string &containerName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETCONTAINERTYPE;

    std::string json = buildGetContainerTypeReq(devId, appName, containerName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 导入数字证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @param {signFlag} 1表示签名证书，0表示加密证书
 * @param {cert} 证书内容(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncImportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName,
                                        const uint32 &signFlag, const std::string &cert) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_IMPORTCERTIFICATE;

    std::string json = buildImportCertificateReq(devId, appName, containerName, signFlag, cert);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 导出数字证书
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @param {signFlag} 1表示签名证书，0表示加密证书
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncExportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName,
                                        const uint32 &signFlag) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXPORTCERTIFICATE;

    std::string json = buildExportCertificatReq(devId, appName, containerName, signFlag);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取证书列表
 * @param {NULL}
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetAllCert() {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETALLCERT;

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 导出公钥
 * @param {devId} 设备ID
 * @param {appName} 应用名称
 * @param {containerName} 容器名称
 * @param {signFlag} 1表示签名公钥，0表示加密公钥
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncExportPublicKey(const std::string &devId, const std::string &appName, const std::string &containerName,
                                      const uint32 &signFlag) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXPORTPUBLICKEY;

    std::string json = buildExportPublicKeyReq(devId, appName, containerName, signFlag);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 外来公钥加密
 * @param {devId} 设备ID
 * @param {pubKey} 公钥(base64编码)
 * @param {type} 1表示RSA,2表示ECC
 * @param {srcData} 源数据(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncExtPubKeyEncrypt(const std::string &devId, const std::string &pubKey, const uint32 &type, const std::string &srcData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXTPUBKEYENCRYPT;

    std::string json = buildExtPubKeyEncryptReq(devId, pubKey, type, srcData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 外来私钥解密
 * @param {devId} 设备ID
 * @param {priKey} 私钥(base64编码)
 * @param {type} 1表示RSA,2表示ECC
 * @param {encryptData} 密文数据(base64编码)
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncExtPriKeyDecrypt(const std::string &devId, const std::string &priKey, const uint32 &type, const std::string &encryptData) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_EXTPRIKEYDECRYPT;

    std::string json = buildExtPriKeyDecryptReq(devId, priKey, type, encryptData);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取provider列表
 * @param {NULL}
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetProviders() {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETPROVIDERS;

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 设置provider
 * @param {name} provider name
 * @param {VPID} provider VPID, like ["055C_F603","055C_F604"]
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncSetProvider(const std::string &name, const std::string &VPID) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_SETPROVIDER;

    std::string json = buildSetProviderReq(name, VPID);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 指纹初始化
 * @param {devId} 设备id
 * @param {type} 指纹类型
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncInitFinger(const std::string &devId, const unsigned int &type) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_INITFINGER;

    std::string json = buildInitFingerReq(devId, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 是否存在指纹
 * @param {devId} 设备id
 * @param {appName} 应用名称
 * @param {type} 指纹类型
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncHasFinger(const std::string &devId, const std::string &appName, const unsigned int &type) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_HASFINGER;

    std::string json = buildHasFingerReq(devId, appName, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 验证指纹
 * @param {devId} 设备id
 * @param {appName} 应用名称
 * @param {type} 指纹类型
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncVerifyFinger(const std::string &devId, const std::string &appName, const unsigned int &type) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_VERIFYFINGER;

    std::string json = buildVerifyFingerReq(devId, appName, type);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 取消指纹
 * @param {devId} 设备id
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncCancleFinger(const std::string &devId) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_SETPROVIDER;

    std::string json = buildCancleFingerReq(devId);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());
    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 创建文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @param {fileSize} 文件大小
 * @param {readRights} 读权限
 * @param {writeRights} 写权限
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncCreateFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &fileSize,
                                 const unsigned int &readRights, const unsigned int &writeRights) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_CERATEFILE;

    std::string json = buildCreateFileReq(devId, appName, fileName, fileSize, readRights, writeRights);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 删除文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncDeleteFile(const std::string &devId, const std::string &appName, const std::string &fileName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_DELETEFILE;

    std::string json = buildDeleteFileReq(devId, appName, fileName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取文件列表
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetFileList(const std::string &devId, const std::string &appName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETFILELIST;

    std::string json = buildGetFileListReq(devId, appName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 获取文件属性
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncGetFileInfo(const std::string &devId, const std::string &appName, const std::string &fileName) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETFILEINFO;

    std::string json = buildGetFileInfoReq(devId, appName, fileName);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 读文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @param {offset} 文件读取偏移位置
 * @param {size} 读取的长度
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncReadFile(const std::string &devId, const std::string &appName, const std::string &fileName, unsigned int &offset,
                               unsigned int &size) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_READFILE;

    std::string json = buildReadFileReq(devId, appName, fileName, offset, size);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(DEVSERVICE, &req, NULL);
}

/**
 * @description: 写文件
 * @param {devId} 设备id
 * @param {appName} 应用名
 * @param {fileName}	文件名
 * @param {offset} 文件读取偏移位置
 * @param {data} 写入的数据
 * @return: reqid，消息id。用于区分消息，与响应消息respid相同
 */
int AsyncDevice::asyncWriteFile(const std::string &devId, const std::string &appName, const std::string &fileName, unsigned int &offset,
                                const std::string &data) {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_WRITEFILE;

    std::string json = buildWriteFileReq(devId, appName, fileName, offset, data);

    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    return reqAsync(DEVSERVICE, &req, NULL);
}
}  // namespace testAgent
}  // namespace koal