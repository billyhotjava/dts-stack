/**
 *  @file syncUnionAuth.h
 *  @brief 测试pkiAgent4c同步接口
 *  @date 2019年12月10日
 *  @author wangtao
 *  @email  wangtao1@koal.com
 */
#ifndef _SYNCUNIONAUTH_H_
#define _SYNCUNIONAUTH_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {
namespace testAgent {
class SyncUnionAuth {
   public:
    int syncGetAuthModule();
    int syncInitAuth();
    int syncGetUserToken();
    int syncRenewUserToken();
    int syncGetAppToken();
    int syncRenewAppToken();
    int syncVerifyAppToken();
    int syncOfflineAppToken();
    int syncVerifyAuth();
    int syncCancleAuth();

   private:
    std::string mLabel;
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_