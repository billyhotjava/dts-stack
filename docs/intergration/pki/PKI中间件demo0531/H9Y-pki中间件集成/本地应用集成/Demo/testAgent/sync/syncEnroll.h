/**
 *  @file syncEnroll.h
 *  @brief 测试pkiAgent4c同步接口
 *  @date 2019年12月10日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _SYNCENROLL_H_
#define _SYNCENROLL_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class SyncEnRoll {
   public:
    int syncMakePkcs10(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& dn,
                       const int& extensionType, const int& reqDigst);
    int syncGenKeypair(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& keyType,
                       const std::string& keyLen, const unsigned int& purpose);
    int syncImportEncKeypair(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& b64Key);
    int syncImportX509Cert(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& b64cert,
                           const std::string& purpose);
    int syncImportPfxCert(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& b64cert,
                          const std::string& certPass);
    int syncGetCert(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& certType);

    int syncImportPfx2SkfFile(const std::string& devId, const std::string& appName, const std::string& conName, unsigned int signFlag,
                              const std::string& certPass, const std::string& b64cert);
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_