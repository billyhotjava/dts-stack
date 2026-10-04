#ifndef _ASYNCOMMON_H_
#define _ASYNCOMMON_H_
#include <iostream>
#include <vector>
#include <map>

namespace koal {

namespace testAgent {
class AsynCommon {
   public:
    int aSynSetTrustedDrives();
    int aSyngetSysInfo();
    int aSyngetLoginTempParam();
};
}  // namespace testAgent
}  // namespace koal

#endif  //_TESTCASE_H_