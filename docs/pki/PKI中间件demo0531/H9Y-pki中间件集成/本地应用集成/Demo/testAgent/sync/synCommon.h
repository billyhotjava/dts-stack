#ifndef _SYNCOMMON_H_
#define _SYNCOMMON_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {

namespace testAgent {
class SynCommon {
   public:
    int SetTrustedDrives();
    int getSysInfo();
    int getLoginTempParam();
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_