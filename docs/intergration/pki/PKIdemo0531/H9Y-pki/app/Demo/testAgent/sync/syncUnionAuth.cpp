#include "syncUnionAuth.h"
#include "pkiAgent4c.h"
#include "jsonProtocol.h"

namespace koal {
namespace testAgent {
int SyncUnionAuth::syncGetAuthModule() {
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
    JSON_Object *auth_obj = json_array_get_object(data_obj, 2);
    if (json_object_has_value(auth_obj, "label")) {
        mLabel = json_object_get_string(auth_obj, "label");
    }

    printf("mLabel=%s\n", mLabel.c_str());
    return resp.errCode;
}

int SyncUnionAuth::syncInitAuth() {
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

int SyncUnionAuth::syncGetUserToken() {
    printf("============================================== getUserToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildUnionAuthReq(mLabel, 0x00, "getUserToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncRenewUserToken() {
    printf("============================================== renewUserToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildUnionAuthReq(mLabel, 0x01, "renewUserToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncGetAppToken() {
    printf("============================================== getAppToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildAppTokenAuthReq(mLabel, 0x02, "111111111111", "getAppToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncRenewAppToken() {
    printf("============================================== renewAppToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildUnionAuthReq(mLabel, 0x03, "renewAppToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncVerifyAppToken() {
    printf("============================================== verifyAppToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildUnionAuthReq(mLabel, 0x04, "verifyAppToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncOfflineAppToken() {
    printf("============================================== offlineAppToken\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_STARAUTH;

    std::string json = buildUnionAuthReq(mLabel, 0x05, "offlineAppToken");
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncVerifyAuth() {
    printf("============================================== VerifyAuth\n");
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_UNIONAUTH_VERIFYAUTH;

    std::string json = buildAuthInitReq(mLabel);
    req.data->setSize(json.length() + 1);
    strncpy((char *)req.data->getData(), json.c_str(), json.length());

    reqSync(UNIAUTHSERVICE, &req, &resp);

    printf("res.data=%s\n", resp.data->getDataString().c_str());
    printf("res.errCode=%#x\n", resp.errCode);
    return resp.errCode;
}

int SyncUnionAuth::syncCancleAuth() {
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
