#include "pkiAgent4c.h"
#include "jsonProtocol.h"
#include "synCommon.h"
#include "parson.h"

namespace koal {
namespace testAgent {

int SynCommon::SetTrustedDrives() {
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
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
    reqSync(COMMONSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SynCommon::getSysInfo() {
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_COMMMON_GETSYSINFO;

    reqSync(COMMONSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SynCommon::getLoginTempParam() {
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_COMMON_GETLOGINTEMPPARAM;

    reqSync(COMMONSERVICE, &req, &resp);
    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}
}  // namespace testAgent
}  // namespace koal