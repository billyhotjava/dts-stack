/**
 *  @file syncDevice.h
 *  @brief 测试pkiAgent4c同步接口
 *  @date 2019年12月10日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _SYNCDEVICE_H_
#define _SYNCDEVICE_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class SyncDevice {
   public:
    int syncGetDevices();
    int syncGetDevInfo(const std::string &devId);
    int syncSetDevLable(const std::string &devId, const std::string &lable);
    int syncTransMitData(const std::string &devId, const std::string &command);
    int syncDevAuth(const std::string &devId, const std::string &authData);
    int syncChangeAuthKey(const std::string &devId, const std::string &authData);
    int syncGetPINInfo(const std::string &devId, const std::string &appName, const unsigned int &PINType);
    int syncChangePIN(const std::string &devId, const std::string &appName, const unsigned int &PINType, const std::string &oldPIN,
                      const std::string &newPIN);
    int syncVerifyPIN(const std::string &devId, const std::string &appName, const unsigned int &PINType, const std::string &PIN);
    int syncGetCachedPIN(const std::string &devId, const std::string &appName, const unsigned int &PINType);
    int syncUnlockPIN(const std::string &devID, const std::string &appName, const std::string &adminPIN, const std::string &userPIN);
    int syncGetAppList(const std::string &devId);
    int syncCreateApp(const std::string &devId, const std::string &appName, const std::string &admin_PIN, const unsigned int &admin_maxRetryCount,
                      const std::string &user_PIN, const unsigned int &user_maxRetryCount, const unsigned int &fileRight);
    int syncDelApp(const std::string &devId, const std::string &appName);
    int syncGetContainers(const std::string &devId, const std::string &appName);
    int syncCreateContainer(const std::string &devId, const std::string &appName, const std::string &containerName);
    int syncDelContainer(const std::string &devId, const std::string &appName, const std::string &containerName);
    int syncGetContainerType(const std::string &devId, const std::string &appName, const std::string &containerName);
    int syncImportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName, const unsigned int &signFlag,
                              const std::string &cert);
    int syncExportCertificate(const std::string &devId, const std::string &appName, const std::string &containerName, const unsigned int &signFlag);
    int syncGetAllCert();
    int syncGetAllCertBySN();
    int syncExportPublicKey(const std::string &devId, const std::string &appName, const std::string &containerName, const unsigned int &signFlag);
    int syncExtPubKeyEncrypt(const std::string &devId, const std::string &pubKey, const unsigned int &type, const std::string &srcData);
    int syncExtPriKeyDecrypt(const std::string &devId, const std::string &priKey, const unsigned int &type, const std::string &encryptData);
    int syncGetProviders();
    int syncSetProvider(const std::string &name, const std::string &VPID);
    int syncUnblockFinger(const std::string &devId, const std::string &appName, const unsigned int &type);
    int syncInitFinger(const std::string &devId, const unsigned int &type);
    int syncHasFinger(const std::string &devId, const std::string &appName, const unsigned int &type);
    int syncVerifyFinger(const std::string &devId, const std::string &appName, const unsigned int &type);
    int syncCancleFinger(const std::string &devId);
    int syncCreateFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &fileSize,
                       const unsigned int &readRights, const unsigned int &writeRights);
    int syncDeleteFile(const std::string &devId, const std::string &appName, const std::string &fileName);
    int syncGetFileList(const std::string &devId, const std::string &appName);
    int syncGetFileInfo(const std::string &devId, const std::string &appName, const std::string &fileName);
    int syncReadFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &offset,
                     const unsigned int &size);
    int syncWriteFile(const std::string &devId, const std::string &appName, const std::string &fileName, const unsigned int &offset,
                      const std::string &data);
    int syncGenRandom(const std::string &devId, const unsigned int &randomLen);

   public:
    std::string syncGetDevID();
    std::string syncGetDevType();

   private:
    std::string mDevID;
    std::string mDevType;
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_