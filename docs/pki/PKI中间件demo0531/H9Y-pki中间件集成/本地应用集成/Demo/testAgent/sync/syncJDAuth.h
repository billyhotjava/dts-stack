/**
 *  @file syncJDAuth.h
 *  @brief 测试pkiAgent4c同步接口
 *  @date 2019年12月10日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _SYNCJDAUTH_H_
#define _SYNCJDAUTH_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class SyncJDAuth {
   public:
    int syncGetAuthModule();
    int syncInitAuth();
    int syncGetToken();
    int syncGetTokenEx();
    int syncGetTokenSpecAuthType();
    int syncGetTokenSpecAuthTypeEx();
    int syncCancleAuth();

   private:
    std::string mLabel;
};

}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_