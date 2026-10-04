#include "pkiAgent4c.h"
#include "jsonProtocol.h"
#include "asynCommon.h"
#include "parson.h"

namespace koal {
namespace testAgent {

int AsynCommon::aSynSetTrustedDrives() {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_COMMMON_SETTRUSTEDDRIVES;
    std::string reqJsonBody = "";

    reqJsonBody = buildSetTrustedDrivesReqArray();
    if (reqJsonBody.empty()) {
        printf("jsonbody Assembly failed !\n");
    }
    req.data->setSize(reqJsonBody.length() + 1);
    strncpy((char *)req.data->getData(), reqJsonBody.c_str(), reqJsonBody.length());
    return reqAsync(COMMONSERVICE, &req, NULL);
}

int AsynCommon::aSyngetSysInfo() {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_COMMMON_GETSYSINFO;

    return reqAsync(COMMONSERVICE, &req, NULL);
}

int AsynCommon::aSyngetLoginTempParam() {
    kpkiReq req;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_COMMON_GETLOGINTEMPPARAM;

    return reqAsync(COMMONSERVICE, &req, NULL);
}
}  // namespace testAgent
}  // namespace koal