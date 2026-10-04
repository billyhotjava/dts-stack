#include "syncJDAuth.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
// JD多认证
int SyncJDAuth::syncGetAuthModule() {
    printf("============================================== GetAuthModule\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x01;
    req.msgType = MSG_UNIONAUTH_GETMODULE;

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);

    JSON_Value *root_value = json_parse_string(resp.data->getDataString().c_str());
    JSON_Object *root_object = json_value_get_object(root_value);
    JSON_Array *data_obj = json_object_get_array(root_object, "data");
    JSON_Object *auth_obj = json_array_get_object(data_obj, 3);
    if (json_object_has_value(auth_obj, "label")) {
        mLabel = json_object_get_string(auth_obj, "label");
    }

    printf("mLabel=%s\n", mLabel.c_str());
    return resp.errCode;
}

int SyncJDAuth::syncInitAuth() {
    printf("============================================== InitAuth\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_INITAUTH;

    std::string json = buildAuthInitReq(mLabel);
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncJDAuth::syncGetToken() {
    printf("============================================== getUserToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildGetTokenReq(0x01, mLabel, 0x01, "", "", "", "", "getToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncJDAuth::syncGetTokenEx() {
    printf("============================================== GetTokenEx\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildGetTokenReq(0x01, mLabel, 0x01, "0547211666485248", "A", "123456", "", "GetTokenEx");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncJDAuth::syncGetTokenSpecAuthType() {
    printf("============================================== getTokenSpecAuthType\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildGetTokenReqByPwd(0x00, mLabel, 0x01, "", "", "", "", "getTokenSpecAuthType", "hejr", "123456");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncJDAuth::syncGetTokenSpecAuthTypeEx() {
    printf("============================================== getTokenSpecAuthTypeEx\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildGetTokenReqByPwd(0x00, mLabel, 0x01, "0547211666485248", "A", "123456", "", "getTokenSpecAuthTypeEx", "hejr", "123456");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncJDAuth::syncCancleAuth() {
    printf("============================================== CancleAuth\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_CANCELAUTH;

    std::string json = buildAuthInitReq(mLabel);
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

}  // namespace testAgent
}  // namespace koal
