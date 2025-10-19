/**
 *  @file asyncEnroll.h
 *  @brief 测试pkiAgent4c异步接口
 *  @date 2019年12月17日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _ASYNCENROLL_H_
#define _ASYNCENROLL_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class AsyncEnRoll {
   public:
    int asyncMakePkcs10(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& dn,
                        const int& extensionType, const int& reqDigst);
    int asyncGenKeypair(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& keyType,
                        const std::string& keyLen, const unsigned int& purpose);
    int asyncImportEncKeypair(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& b64Key);
    int asyncImportX509Cert(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& b64cert,
                            const std::string& purpose);
    int asyncImportPfxCert(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& b64cert,
                           const std::string& certPass);
    int asyncGetCert(const std::string& devId, const std::string& appName, const std::string& conName, const std::string& certType);
    int asyncImportPfx2SkfFile(const std::string& devId, const std::string& appName, const std::string& conName, unsigned int signFlag,
                               const std::string& certPass, const std::string& b64cert);
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_