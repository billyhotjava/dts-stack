/**
 *  @file asyncDevice.h
 *  @brief 测试pkiAgent4c异步接口
 *  @date 2019年12月17日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _ASYNCDEVICE_H_
#define _ASYNCDEVICE_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class AsyncDevice {
   public:
    int asyncGetDevices();
    int asyncGetDevInfo(const std::string &devId);
    int asyncSetDevLable(const std::string &devId, const std::string &lable);
    int asyncTransMitData(const std::string &devId, const std::string &command);
    int asyncDevAuth(const std::string &devId, const std::string &authData);
    int asyncChangeAuthKey(const std::string &devId, const std::string &authData);
    int asyncGetPINInfo(const std::string &devId, const std::string &appName, const unsigned int &PINType);
    int asyncChangePIN(const std::string &devId, const std::string &appName, const unsigned int &PINType, const std::string &oldPIN,
                       const std::string &newPIN);
    int asyncVerifyPIN(const std::string &devId, const std::string &appName, const unsigned int &PINType, const std::string &PIN);
    int asyncUnlockPIN(const std::string &devId, const std::string &appName, const std::string &adminPIN, const std::string &userPIN);
    int asyncGetAppList(const std::string &devId);
    int asyncCreateApp(const std::string &devId, const std::string &appName, const std::string &admin_PIN, const unsigned int &admin_maxRetryCount,
                       const std::string &user_PIN, const unsigned int &user_maxRetryCount, const unsigned int &fileRight);
    int asyncDelApp(const std::string &devId, const std::string &appName);
    int asyncGetContainers(const std::string &devId, const std::string &appName);
    int asyncCreateContainer(const std::string &devId, const std::string &appName, const std::string &containerName);
    int asyncDelContainer(const std::string &devId, const std::string &appName, const std::string &containerName);
    int asyncGetContainerType(const std::string &devId, const std::string &appName, const std::string &containerName);
    int asyncImportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName, const unsigned int &signFlag,
                               const std::string &cert);
    int asyncExportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName, const unsigned int &signFlag);
    int asyncGetAllCert();
    int asyncExportPublicKey(const std::string &devId, const std::string &appName, const std::string &containerName, const unsigned int &signFlag);
    int asyncExtPubKeyEncrypt(const std::string &devId, const std::string &pubKey, const unsigned int &type, const std::string &srcData);
    int asyncExtPriKeyDecrypt(const std::string &devId, const std::string &priKey, const unsigned int &type, const std::string &encryptData);
    int asyncGetProviders();
    int asyncSetProvider(const std::string &name, const std::string &VPID);
    int asyncInitFinger(const std::string &devId, const unsigned int &type);
    int asyncHasFinger(const std::string &devId, const std::string &appName, const unsigned int &type);
    int asyncVerifyFinger(const std::string &devId, const std::string &appName, const unsigned int &type);
    int asyncCancleFinger(const std::string &devId);
    int asyncCreateFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &fileSize,
                        const unsigned int &readRights, const unsigned int &writeRights);
    int asyncDeleteFile(const std::string &devId, const std::string &appName, const std::string &fileName);
    int asyncGetFileList(const std::string &devId, const std::string &appName);
    int asyncGetFileInfo(const std::string &devId, const std::string &appName, const std::string &fileName);
    int asyncReadFile(const std::string &devId, const std::string &appName, const std::string &fileName, unsigned int &offset, unsigned int &size);
    int asyncWriteFile(const std::string &devId, const std::string &appName, const std::string &fileName, unsigned int &offset,
                       const std::string &data);

   public:
    std::string asyncGetDevID();
    bool asyncSetDevID(std::string devId);

   private:
    std::string mDevID;
};
}  // namespace testAgent
}  // namespace koal

#endif  //_ASYNCDEVICE_H_