/**
 * @brief pkiAgent4c的测试文件，同步请求由测试工具doctest测试，异步请求仅写了部分示例
 * @author wangtao
 * @email wangtao1@koal.com
 */

#include <stdio.h>
#include "pkiAgent4c.h"
#include "jsonProtocol.h"
#include "parson.h"
using namespace koal::testAgent;

#ifndef ASYNC            //同步
#ifdef DOCTEST           //同步doctest
#include "testCase.hpp"  ///同步请求,启动doctest测试
#else                    //同步demo
#include "sync/syncDevice.h"
#include "sync/syncEnroll.h"
#include "sync/syncSignx.h"
#include "sync/syncUnionAuth.h"
#include "sync/syncJDAuth.h"
#include "sync/synCommon.h"

SyncDevice gDevice;
SyncSignX gSignx;
SyncEnRoll gEnRoll;
SynCommon gCommon;

struct CertInfo {
    std::string devId;
    std::string manufacturer;
    std::string appName;
    std::string conName;
    std::string SN;
    std::string issuer;
    std::string subject;
    std::string starttime;
    std::string endtime;
    std::string certType;
} certInfo;

std::map<int, CertInfo> mapParseData;  ///用于存放parseGetcertlist接口返回的数据

bool parseGetcertlist(std::string strSrc, std::map<int, CertInfo> &mapParseData) {
    bool bRet = false;
    JSON_Value *pJson = json_parse_string(strSrc.c_str());
    if (pJson == NULL) {
        return false;
    }
    JSON_Object *rootObject = json_value_get_object(pJson);
    if (rootObject == NULL) {
        json_value_free(pJson);
        return false;
    }
    do {
        JSON_Array *pArray = NULL;
        pArray = json_object_get_array(rootObject, "certs");
        if (pArray == NULL) {
            break;
        }
        int iCount = json_array_get_count(pArray);

        for (int i = 0; i < iCount; i++) {
            CertInfo certInfo;
            JSON_Object *item = json_array_get_object(pArray, i);
            if (!json_object_has_value(item, "devID")) {
                break;
            }
            certInfo.devId = json_object_get_string(item, "devID");

            if (!json_object_has_value(item, "manufacturer")) {
                break;
            }
            certInfo.appName = json_object_get_string(item, "appName");

            if (!json_object_has_value(item, "containerName")) {
                break;
            }
            certInfo.conName = json_object_get_string(item, "containerName");

            if (!json_object_has_value(item, "SN")) {
                break;
            }
            certInfo.SN = json_object_get_string(item, "SN");
            if (!json_object_has_value(item, "subjectName")) {
                break;
            }
            JSON_Object *subject_obj = json_object_get_object(item, "subjectName");
            if (!subject_obj) {
                break;
            }
            if (!json_object_has_value(subject_obj, "CN")) {
                break;
            }
            certInfo.subject = json_object_get_string(subject_obj, "CN");

            if (!json_object_has_value(item, "issuerName")) {
                break;
            }
            JSON_Object *issuer_obj = json_object_get_object(item, "issuerName");
            if (!issuer_obj) {
                break;
            }
            if (!json_object_has_value(issuer_obj, "CN")) {
                break;
            }
            certInfo.issuer = json_object_get_string(issuer_obj, "CN");
            mapParseData[i] = certInfo;
            bRet = true;
        }

    } while (0);
    if (pJson) {
        json_value_free(pJson);
        pJson = NULL;
    }
    return bRet;
}

bool getAllCertList(std::string &certList) {
    kpkiReq req;
    kpkiResp resp;
    req.reqid = 1;
    req.version = 0x01;
    req.extend = 0x00;
    req.msgType = MSG_DEVICE_GETALLCERT;

    bool bRet = reqSync(DEVSERVICE, &req, &resp);
    if (bRet) {
        certList = resp.data->getDataString();
    }
    return bRet;
}

#endif
#else  //异步
#include "async/asyncDevice.h"
#include "async/asyncEnroll.h"
#include "async/asyncSignx.h"
#include "async/asynCommon.h"

AsyncDevice gDevice;
AsyncSignX gSignx;
AsyncEnRoll gEnRoll;
AsynCommon gCommon;
std::string gDevId;
std::map<int, std::string> mapRespData;  ///用于保存异步返回的消息id及数据，mapRespData[respid] = resp.jsonBody

bool resp_DEVICESERVICE(const kpkiResp *pResp, void *pUserData) {
    /* device */
    switch (pResp->msgType) {
        case MSG_DEVICE_GETDEVICES: {
            printf("=========================================================== getDevices\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            std::vector<std::map<std::string, std::string> > arrayDevID;
            parseGetDevicesResponse(pResp->data->getDataString(), arrayDevID);
            //提取一个devID
            std::vector<std::map<std::string, std::string> >::iterator it = arrayDevID.begin();
            if (it == arrayDevID.end()) {
                printf("without any device, process exit\n");
                return false;
            }
            AsyncDevice *pDevice = (AsyncDevice *)pUserData;
            pDevice->asyncSetDevID((*it)["devID"]);

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETDEVINFO: {
            printf("=========================================================== getDevInfo\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_SETDEVLABLE: {
            printf("=========================================================== setDevLable\n");
            printf("res.respid=%d\n", pResp->respid);

            if (0 == pResp->errCode) {
                printf("successful...\n");
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_TRANSMITDATA: {
            printf("=========================================================== transMitData\n");
            printf("res.respid=%d\n", pResp->respid);

            if (0 == pResp->errCode) {
                printf("successful...\n");
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_DEVAUTH: {
            printf("============================================== devAuth\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_CHANGEAUTHKEY: {
            printf("============================================== changeAuthKey\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETPININFO: {
            printf("============================================== getPINInfo\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_CHANGEPIN: {
            printf("============================================== changePIN\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_VERIFYPIN: {
            printf("============================================== verifyPIN\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_UNLOCKPIN: {
            printf("============================================== unlockPIN\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETAPPLIST: {
            printf("============================================== getAppList\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_CREATEAPP: {
            printf("============================================== createApp\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_DELAPP: {
            printf("============================================== delApp\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETCONTAINERS: {
            printf("============================================== getContainers\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_CREATECONTAINER: {
            printf("============================================== createContainer\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_DELCONTAINER: {
            printf("============================================== delContainer\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETCONTAINERTYPE: {
            printf("============================================== getContainerType\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_IMPORTCERTIFICATE: {
            printf("============================================== importCertificate\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_EXPORTCERTIFICATE: {
            printf("============================================== exportCertificate\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_EXPORTPUBLICKEY: {
            printf("============================================== exportPublicKey\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETPROVIDERS: {
            printf("============================================== getProviders\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_SETPROVIDER: {
            printf("============================================== setProvider\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_EXTPUBKEYENCRYPT: {
            printf("============================================== extPubKeyEncrypt\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_EXTPRIKEYDECRYPT: {
            printf("============================================== extPriKeyDecrypt\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_DEVICE_GETALLCERT: {
            printf("============================================== GetAllCert\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        default:
            break;
    }
    return true;
}

bool resp_ENROLLSERVICE(const kpkiResp *pResp, void *pUserData) {
    /* enRoll  */
    switch (pResp->msgType) {
        case MSG_ENROLL_MKP10: {
            printf("============================================== makePkcs10\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());
            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_ENROLL_KEYPAIR: {
            printf("============================================== genKeypair\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());
            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_ENROLL_IMPORTKEYPAIR: {
            printf("============================================== importEncKeypair\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_ENROLL_IMPORTX509: {
            printf("============================================== importX509Cert\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());
            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_ENROLL_IMPORTPFX: {
            printf("============================================== importPfxCert\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());
            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_ENROLL_GETCERT: {
            printf("============================================== getCert\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());
            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        default:
            break;
    }
    return true;
}

bool resp_SIGNXLSERVICE(const kpkiResp *pResp, void *pUserData) {
    /* Signx */
    switch (pResp->msgType) {
        case MSG_SIGNX_SIGNDATA: {
            printf("============================================== signData\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }

            /// 获取签名结果
            AsyncSignX *pSignx = (AsyncSignX *)pUserData;
            std::string signData;
            parseSignDatResp(pResp->data->getDataString(), signData);
            pSignx->asyncSetSignData(signData);
        } break;
        case MSG_SIGNX_VERIFYSIGN: {
            printf("============================================== verifySignData\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_SIGNP7: {
            printf("============================================== signMessage\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }

            /// 获取签名结果
            AsyncSignX *pSignx = (AsyncSignX *)pUserData;
            std::string P7SignData;
            parsePkcs7SignResp(pResp->data->getDataString(), P7SignData);
            pSignx->asyncSetP7SignData(P7SignData);
        } break;
        case MSG_SIGNX_VERIFYSIGNP7: {
            printf("============================================== verifyMessage\n");
            printf("res.respid=%d\n", pResp->respid);

            if (0 == pResp->errCode) {
                printf("successful...\n");
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_EXTECCPUBVERIFY: {
            printf("============================================== extECCVerify\n");
            printf("res.respid=%d\n", pResp->respid);

            if (0 == pResp->errCode) {
                printf("successful...\n");
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_EXTECCCERTVERIFY: {
            printf("============================================== extECCVerifyEx\n");
            printf("res.respid=%d\n", pResp->respid);

            if (0 == pResp->errCode) {
                printf("successful...\n");
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_MKCERTFROMTEMP: {
            printf("============================================== dupCertWithTemplate\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_PARSECERT: {
            printf("============================================== parseCert\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_ENVELOPEENC: {
            printf("============================================== envelopeEncrypt\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }

            AsyncSignX *pSignx = (AsyncSignX *)pUserData;
            std::string envelopeEncrypt;
            parseEnvelopeEncryptResp(pResp->data->getDataString(), envelopeEncrypt);
            pSignx->asyncSetEnvelopeEncryptData(envelopeEncrypt);
        } break;
        case MSG_SIGNX_ENVELOPEDEC: {
            printf("============================================== envelopeDecrypt\n");
            printf("res.respid=%d\n", pResp->respid);
            printf("res.data=%s\n", pResp->data->getDataString().c_str());

            if (0 == pResp->errCode) {
                printf("successful...\n");
                mapRespData[pResp->respid] = pResp->data->getDataString();
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        case MSG_SIGNX_EXTECCCERTVERIFYP7: {
            printf("============================================== verifySignedMessage\n");
            printf("res.respid=%d\n", pResp->respid);

            if (0 == pResp->errCode) {
                printf("successful...\n");
            } else {
                printf("failure..., res.errCode=%#x\n", pResp->errCode);
            }
        } break;
        default:
            break;
    }
    return true;
}

bool callBack(const PKI_SERVICE *pSvc, const kpkiResp *pResp, void *pUserData) {
    if (NULL == pSvc || NULL == pResp) {
        printf("CallBack error, pSvc=%#x, pResp=%#x", pSvc, pResp);
        return false;
    }

    PKI_SERVICE svc = *pSvc;
    switch (svc) {
        case ENROLLSERVICE:
            resp_ENROLLSERVICE(pResp, pUserData);
            break;
        case DEVSERVICE:
            resp_DEVICESERVICE(pResp, pUserData);
            break;
        case SIGNXSERVICE:
            resp_SIGNXLSERVICE(pResp, pUserData);
            break;
        default:
            break;
    }
    return true;
}
#endif

bool msgNotify(const kpkiResp *pResp, void *pUserData) {
    if (pUserData) {
        const char *pData = (const char *)pUserData;
        printf("get parm=\"%s\" from callBack msgNotify\n", pData);
    }

    switch (pResp->msgType) {
        case 0x0FFF0001: {  ///设备插入
            printf("key insert\n");
            break;
        }
        case 0x0FFF0002: {  ///设备拔出
            printf("key remove\n");
            break;
        }
        case 0x0FFF0003: {  ///设备修改
            printf("key changed\n");
            break;
        }
        case 0x0FFF0004: {  /// Session关闭
            printf("session closed\n");
            break;
        }
        case 0x0FFF0031: {
            printf("get user token\n");
            break;
        }
    }
    return true;
}

#ifndef ASYNC  ///同步请求
int main(int argc, char **argv) {
#ifdef DOCTEST  //同步doctest
    doctest::Context context(argc, argv);
    int testResult = 0;

    const char *pTest = "this is testing";
    do {
        ///初始化AGENT
        if (!createAgent(&msgNotify, NULL, (void *)pTest)) {
            printf("create Agent failed\n");
            break;
        }

        /*
            数据签名接口调用顺序，如下：
        */

        /*
            登录Agent，对应接口规范中login接口
            接口参数，由格尔统一分配，该接口参数分配后为固定值，不可随意更换
            @param {appName}应用名称
            @param {appID}应用ID
            @param {token}应用令牌
        */
        if (!loginAgent("11111-111", "22222-222", "33333-33")) {
            printf("login failed");
            break;
        }
        /// do something
        // 启动doctest测试用例
        testResult = context.run();
        ///登出
        logoutAgent();
        ///释放agent
        releaseAgent();
    } while (0);

    if (context.shouldExit()) {
        return testResult;
    }

    return 0;
}
#else  ///同步请求demo
    const char *pTest = "this is testing";
    do {
        ///初始化AGENT
        if (!createAgent(&msgNotify, NULL, (void *)pTest)) {
            printf("create Agent failed\n");
            break;
        }

        /*
            数据签名接口调用顺序（登录 -》 获取证书列表 -》 校验PIN码 -》 数据签名\pkcs7签名），
                        详细示例如下：
        */

        /*
            登录Agent，对应接口规范中login接口
            接口参数，由格尔统一分配，该接口参数分配后为固定值，不可随意更换
            @param {appName}应用名称
            @param {appID}应用ID
            @param {token}应用令牌
        */
        if (!loginAgent("11111-111", "22222-222", "33333-33")) {
            printf("login failed");
            break;
        }

#if 0 //暂未开放
        /*
         * 设置可信驱动，同一驱动不同平台对应的信息可调用getSysInfo接口区分
         */
        gCommon.getSysInfo();
        int resTru = gCommon.SetTrustedDrives();
        if (resTru != 0 || resTru != 9) {
            printf("setTrustedDri failure...\n");
            break;
        }
#endif
        /*
            获取证书列表接口
            可获取设备ID、设备名、应用名等参数
            签名时可不用调用syncGetDevices、syncGetDevID测试接口
            直接解析该接口获取的参数
        */
        std::string List;  //用来存放getAllCertList接口掉用后获取到的数据
        getAllCertList(List);

        /*
            解析证书列表接口参数
        */
        parseGetcertlist(List, mapParseData);

        /*
            遍历parseGetcertlist接口返回值
            依次获取到SN，选择自己需要的SN（可根据需求可弹框让用户选择）
            根据选择的SN，获取SN对应设备的devId、appName等值
        */
        std::map<int, CertInfo>::iterator mapParseInfo;
        for (mapParseInfo = mapParseData.begin(); mapParseInfo != mapParseData.end(); mapParseInfo++) {
            printf("number=%d SN=%s\n", mapParseInfo->first, mapParseInfo->second.SN.c_str());
        }

        while (1) {
            if (0 == mapParseData.size()) {
                printf("certs is NULL\n");
                return 0;
            } else {
                printf("please input number:");
                char c;
                ///采用%*忽略输入流中剩余的字符
                scanf("%c%*c", &c);
                std::map<int, CertInfo>::iterator mapKeyInfo;
                int keyValue = atoi(&c);
                mapKeyInfo = mapParseData.find(keyValue);
                if (mapKeyInfo == mapParseData.end()) {
                    printf(" error number,keyValue=%d\n", keyValue);
                    continue;
                } else {
                    /*
                        PIN码校验，需要传应用对应的pin码
                        接口参数设备ID、应用名称使用【获取证书列表】接口获取到的参数即可
                        其余参数见syncVerifyPIN实现函数
                   */
                    gDevice.syncVerifyPIN(mapKeyInfo->second.devId, mapKeyInfo->second.appName, 1, "111111");

                    /*
                        数据签名
                        接口参数设备ID、应用名称、容器名称使用【获取证书列表】接口获取到的参数即可
                        其余参数见syncSignData实现函数
                    */
                    // gSignx.syncSignData(mapKeyInfo->second.devId,mapKeyInfo->second.appName,mapKeyInfo->second.conName, ...);

                    /*
                        P7签名
                        接口参数设备ID、应用名称、容器名称使用【获取证书列表】接口获取到的参数即可
                        其余参数见syncSignMessage实现函数
                    */
                    // gSignx.syncSignMessage(mapKeyInfo->second.devId,mapKeyInfo->second.appName,mapKeyInfo->second.conName, ...);
                    break;
                }
            }
        }
    } while (0);

    /// Sleep(3 * 1000);
    ///登出
    logoutAgent();
    ///释放agent
    releaseAgent();
    return 0;
}
#endif
#else  ///异步请求demo
int main(int argc, char **argv) {
    std::list<int> listReqId;  ///管理异步请求的消息id
    int reqid = 0;

    ///初始化AGENT
    do {
        if (!createAgent(&msgNotify, &callBack)) {
            printf("create Agent failed\n");
            break;
        }
        /*
            数据签名接口调用顺序，如下：
        */

        ///登录，接口参数由格尔统一指定、管理
        if (!loginAgent("11111-111", "22222-222", "33333-33")) {
            printf("login failed");
            break;
        }
        gCommon.aSynSetTrustedDrives();
        gCommon.aSyngetSysInfo();
        /// 获取设备ID,测试接口
        gDevId = gDevice.asyncGetDevID();

        /*
            获取证书列表接口
            可获取设备ID、设备名、应用名等参数
            签名时可不用调用asyncGetDevices、asyncGetDevID测试接口
            直接解析该接口获取的参数
        */
        reqid = gDevice.asyncGetAllCert();
        listReqId.push_back(reqid);

        /*
            PIN码校验
            接口参数设备ID、应用名称使用【获取证书列表】接口获取到的参数即可
            其余参数见asyncVerifyPIN实现函数
        */
        reqid = gDevice.asyncVerifyPIN(gDevId, "ASA", 1, "111111");
        listReqId.push_back(reqid);
        /*
            数据签名
            接口参数设备ID、应用名称、容器名称使用【获取证书列表】接口获取到的参数即可
            其余参数见asyncGetSignData实现函数
        */
        /// reqid = gSignx.asyncGetSignData(gDevId, "", ...);

    } while (0);

    Sleep(3 * 1000);
    /**
        说明：若异步请求调用结束后，直接调用logoutAgent、releaseAgent可能会导致部分异步接口请求的数据未处理完。
                    故logoutAgent、releaseAgent交由上层,根据对应业务场景需求，决定何时调用登出、释放接口。
    */
    ///登出
    logoutAgent();
    ///释放agent
    releaseAgent();
    return 0;
}
#endif
