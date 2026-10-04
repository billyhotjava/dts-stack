/**
 *  @file asyncSignx.h
 *  @brief 测试pkiAgent4c异步接口
 *  @date 2019年12月17日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _ASYNCSIGNX_H_
#define _ASYNCSIGNX_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class AsyncSignX {
   public:
    int asyncSignData(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData,
                      const unsigned int& isBase64SrcData, const std::string& type);
    int asyncVerifySignData(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData,
                            const std::string& signData, const unsigned int& isBase64SrcData, const unsigned int& type);
    int asyncSignMessage(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData,
                         const unsigned int& mdType, const std::string& attachData, const unsigned int& signwithSM2Std, const unsigned int& noAttr);
    int asyncVerifyMessage(const std::string& srcData, const std::string& signData);
    int asyncExtECCVerify(const std::string& devId, const std::string& pubkey, const std::string& srcData, const std::string& signData);
    int asyncExtECCVerifyEx(const std::string& devId, const std::string& b64cert, const std::string& srcData, const std::string& signData);
    int asyncDupCertWithTemplate(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& signFlag);
    int asyncParseCert(const std::string cert);
    int asyncEnvelopeEncrypt(const std::string& srcData, const std::string& cert, const unsigned int& cihperType);
    int asyncEnvelopeDecrypt(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData);
    int asyncVerifySignedMessage(const std::string& srcData, const std::string& signData, const std::string& cert);
    int asyncGetExtension(const std::string& cert, const std::string& oid);

   public:
    std::string& asyncGetSignData();
    bool asyncSetSignData(std::string data);
    std::string& asyncGetP7SignData();
    bool asyncSetP7SignData(std::string data);
    std::string& asyncGetEnvelopeEncryptData();
    bool asyncSetEnvelopeEncryptData(std::string data);

   private:
    std::string mSignData;
    std::string mP7SignData;
    std::string mEnvelopeEncrypt;
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_