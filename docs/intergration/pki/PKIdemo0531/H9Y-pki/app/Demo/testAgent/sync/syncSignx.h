/**
 *  @file syncSignx.h
 *  @brief 测试pkiAgent4c同步接口
 *  @date 2019年12月10日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _SYNCSIGNX_H_
#define _SYNCSIGNX_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class SyncSignX {
   public:
    int syncSignData(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData,
                     const unsigned int& isBase64SrcData, const std::string& type);
    int syncVerifySignData(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData,
                           const std::string& signData, const unsigned int& isBase64SrcData, const unsigned int& type);
    int syncSignMessage(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData,
                        const unsigned int& mdType, const std::string& attachData, const unsigned int& signwithSM2Std, const unsigned int& noAttr);
    int syncVerifyMessage(const std::string& srcData, const std::string& signData);
    int syncExtECCVerify(const std::string& devId, const std::string& pubkey, const std::string& srcData, const std::string& signData);
    int syncExtECCVerifyEx(const std::string& devId, const std::string& b64cert, const std::string& srcData, const std::string& signData);
    int syncDupCertWithTemplate(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& signFlag);
    int syncParseCert(const std::string cert);
    int syncEnvelopeEncrypt(const std::string& srcData, const std::string& cert, const unsigned int& cihperType);
    int syncEnvelopeDecrypt(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& srcData);
    int syncVerifySignedMessage(const std::string& srcData, const std::string& signData, const std::string& cert);
    int syncGetExtension(const std::string& cert, const std::string& oid);

   public:
    std::string& syncGetSignData();
    std::string& syncGetP7SignData();
    std::string& syncGetEnvelopeEncryptData();

   private:
    std::string mSignData;
    std::string mP7SignData;
    std::string mEnvelopeEncrypt;
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_