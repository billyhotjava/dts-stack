/**
 *  @file jsonProtocol.h
 *  @brief koalService,notifService接口中使用的结构体与json的转换，
 *  @date 2019年9月10日
 *  @author sharp
 *  @email  yangcp@koal.com
 */
#ifndef THIS_IS_JSON_PROTOCOL_HEADER_FROM_SHARP
#define THIS_IS_JSON_PROTOCOL_HEADER_FROM_SHARP
#include <string>
#include <vector>
#include <list>
#include <map>
#include "parson/parson.h"
#include "stdio.h"
#include "stdlib.h"

#ifdef _WIN32
typedef char int8;
typedef unsigned char uint8;
typedef short int16;
typedef unsigned short uint16;
typedef int int32;
typedef unsigned int uint32;
typedef __int64 int64;
typedef unsigned __int64 uint64;
#include <Ws2tcpip.h>
#include <Winsock2.h>

#pragma comment(lib, "Ws2_32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma warning(disable : 4819)

#elif defined(__linux__)
#include <stdint.h>
#include <pthread.h>
#include <semaphore.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>
#include <semaphore.h>
#include <stdarg.h>

typedef int8_t int8;
typedef uint8_t uint8;
typedef int16_t int16;
typedef uint16_t uint16;
typedef int32_t int32;
typedef uint32_t uint32;
typedef int64_t int64;
typedef uint64_t uint64;

#elif defined(__APPLE__)
#include <stdint.h>
#include <pthread.h>
#include <semaphore.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>
#include <semaphore.h>
#include <stdarg.h>

typedef int8_t int8;
typedef uint8_t uint8;
typedef int16_t int16;
typedef uint16_t uint16;
typedef int32_t int32;
typedef uint32_t uint32;
typedef int64_t int64;
typedef uint64_t uint64;

#endif

#if _MSC_VER
#define snprintf _snprintf
#endif

#ifndef WIN32
#define _atoi64(val) strtoll(val, NULL, 10)
#endif

#define SAFE_PARSE_JSON_BEGIN()                             \
    JSON_Value* pJson = json_parse_string(strSrc.c_str());  \
    if (pJson == NULL) {                                    \
        return false;                                       \
    }                                                       \
    JSON_Object* rootObject = json_value_get_object(pJson); \
    if (rootObject == NULL) {                               \
        json_value_free(pJson);                             \
        return false;                                       \
    }                                                       \
    const char* pValue = NULL;

#define SAFE_GET_JSON_STRING(_X_, _Y_)                \
    pValue = json_object_get_string(rootObject, _X_); \
    if (pValue) {                                     \
        _Y_ = pValue;                                 \
    } else {                                          \
        json_value_free(pJson);                       \
        return false;                                 \
    }

#define SAFE_GET_JSON_STRING2I64(_X_, _Y_)            \
    pValue = json_object_get_string(rootObject, _X_); \
    if (pValue) {                                     \
        _Y_ = _atoi64(pValue);                        \
    } else {                                          \
        json_value_free(pJson);                       \
        return false;                                 \
    }

#define SAFE_GET_JSON_STRING2I32(_X_, _Y_)            \
    pValue = json_object_get_string(rootObject, _X_); \
    if (pValue) {                                     \
        _Y_ = atoi(pValue);                           \
    } else {                                          \
        json_value_free(pJson);                       \
        return false;                                 \
    }

#define SAFE_DOGET_JSON_STRING(_X_, _Y_)                 \
    pValue = json_object_dotget_string(rootObject, _X_); \
    if (pValue) {                                        \
        _Y_ = pValue;                                    \
    } else {                                             \
        json_value_free(pJson);                          \
        return false;                                    \
    }

#define SAFE_DOGET_JSON_STRING2I64(_X_, _Y_)             \
    pValue = json_object_dotget_string(rootObject, _X_); \
    if (pValue) {                                        \
        _Y_ = _atoi64(pValue);                           \
    } else {                                             \
        json_value_free(pJson);                          \
        return false;                                    \
    }

#define SAFE_DOGET_JSON_STRING2I32(_X_, _Y_)             \
    pValue = json_object_dotget_string(rootObject, _X_); \
    if (pValue) {                                        \
        _Y_ = atoi(pValue);                              \
    } else {                                             \
        json_value_free(pJson);                          \
        return false;                                    \
    }

#define SAFE_PARSE_JSON_END() \
    json_value_free(pJson);   \
    return true;

#define SAFE_BUILD_JSON_BEGIN()                             \
    JSON_Value* pJson = json_value_init_object();           \
    JSON_Object* rootObject = json_value_get_object(pJson); \
    char buffer[64] = "";
///设置64位型数字
#define SAFE_SET_JSON_STRING2I64(_X_, _Y_) \
    snprintf(buffer, 64, "%lld", _Y_);     \
    json_object_set_string(rootObject, _X_, buffer);
///设置32位整型数字
#define SAFE_SET_JSON_STRING2I32(_X_, _Y_) \
    snprintf(buffer, 64, "%d", _Y_);       \
    json_object_set_string(rootObject, _X_, buffer);

#define SAFE_SET_JSON_STRING(_X_, _Y_) json_object_set_string(rootObject, _X_, _Y_.c_str());

#define SAFE_SET_JSON_NUMBER(_X_, _Y_) json_object_set_number(rootObject, _X_, _Y_);

#define SAFE_DOTSET_JSON_STRING(_X_, _Y_) json_object_dotset_string(rootObject, _X_, _Y_.c_str());

#define SAFE_DOTSET_JSON_STRING2I32(_X_, _Y_) \
    snprintf(buffer, 64, "%d", _Y_);          \
    json_object_dotset_string(rootObject, _X_, buffer);

#define SAFE_DOSET_JSON_VALUE(_X_, _Y_) json_object_dotset_value(rootObject, _X_, json_parse_string(_Y_.c_str()));

///设置64位型数字
#define SAFE_DOSET_JSON_STRING2I64(_X_, _Y_) \
    snprintf(buffer, 64, "%lld", _Y_);       \
    json_object_dotset_string(rootObject, _X_, buffer);

///设置32位整型数字
#define SAFE_DOSET_JSON_STRING2I32(_X_, _Y_) \
    snprintf(buffer, 64, "%d", _Y_);         \
    json_object_dotset_string(rootObject, _X_, buffer);

#define SAFE_BUILD_JSON_END()                                    \
    std::string strOut = json_serialize_to_string_pretty(pJson); \
    json_value_free(pJson);                                      \
    return strOut;

namespace koal {
namespace testAgent {

/**
   请求错误回复
 */
std::string buildreqErrorResp(const std::string& err);

/*## 获取中间件初始化状态  path="koalService/getInitStat"
msgType = 0x00 ;  ///请求类型

### 请求:
```
version = 0x01 ;  ///消息版本  body json模板的版本
extend  = 0x0 ;
```

*/
std::string buildGetInitStatResp(unsigned int nInitStat);

/*## 1. 登录消息  path="koalService/login"
msgType = 0x01 ;  ///请求类型

### 请求:
```
version = 0x01 ;  ///消息版本  body json模板的版本
extend  = 0x0 ;
jsonBody = {
    "appName":"",   ---  应用名称, 如果是网页的话，则为host字段
    "appID":"",     ---  应用ID
    "token":"",     ---  应用令牌
}
```
*/
bool parseLoginReq(const std::string& strSrc, std::string& appName, std::string& appID, std::string& token);

/**
 *  "sessionID":"",
    "ticket":"",
    "notifyPort":"", --- 推送使用的端口
    "timeout":"",  --- session过期时间,单位秒
 */
std::string buildLoginResp(int64 sessionID, std::string& ticket, int32 notifyerPort, int32 timeOut);

/*## 2. 注销 path = "koalService/logout"
msgType = 0x02 ;  ///请求类型
```
  应用程序注销中间件的登录状态,该接口不关心返回值，通知服务器即可

jsonBody =
{
    "sessionID":"",   ---  会话ID
    "ticket":"",     ---  登录票据
}
*/
bool parseLogOutReq(const std::string& strSrc, int64& sessionID, std::string& ticket);

/**
 * jsonBody =
{
    "devID":"",         ---  系统自定义的设备的编号
    "devNumber":"",     ---  设备编号，设备自带
    "devLable":""       ---  设备标签, 可以用户设置
}
 */
std::string buildDeviceInResp(const std::string& devID, const std::string& devNumber, const std::string& devLable);
///拔出
std::string buildDeviceOutResp(const std::string& devID, const std::string& devNumber, const std::string& devLable);

bool parseGetDevicesResponse(const std::string& strSrc, std::vector<std::map<std::string, std::string> >& array);

/**
jsonBody =
{
    "devID":""                  --- 设备ID
}
*/
std::string buildGetDevInfoReq(const std::string& devID);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "lable":""                  --- 标签
}
*/
std::string buildSetDevLableReq(const std::string& devID, const std::string& lable);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "command":""                --- 命令二进制数据的base64编码
}
*/
std::string buildTransMitDataReq(const std::string& devID, const std::string& command);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "authData":""               --- 认证二进制数据的base64编码
}
*/
std::string buildDevAuthReq(const std::string& devID, const std::string& authData);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "authKeyData":""            --- 认证二进制数据的base64编码
}
*/
std::string buildChangeAuthKeyReq(const std::string& devID, const std::string& authKeyData);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":""                --- PIN类型
}
*/
std::string buildGetPINInfoReq(const std::string& devID, const std::string& appName, const uint32& PINType);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":"",               --- PIN类型
    "oldPIN":"",                --- 老的PIN码
    "newPIN":""                 --- 新的PIN码
}
*/
std::string buildChangePINReq(const std::string& devID, const std::string& appName, const uint32& PINType, const std::string& oldPIN,
                              const std::string& newPIN);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":"",               --- PIN类型
    "PIN"    :""                --- PIN码
}
*/
std::string buildVerifyPINReq(const std::string& devID, const std::string& appName, const uint32& PINType, const std::string& PIN);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":""                --- PIN类型
}
*/
std::string buildGetCachedPINReq(const std::string& devID, const std::string& appName, const uint32& PINType);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "adminPIN":"",              --- 管理员PIN码
    "userPIN":""                --- 用户PIN码
}
*/
std::string buildUnlockPINReq(const std::string& devID, const std::string& appName, const std::string& adminPIN, const std::string& userPIN);

/**
jsonBody =
{
    "devID":""                  --- 设备ID
}
*/
std::string buildGetAppListReq(const std::string& devID);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "admin":{
        "PIN":"",               --- 管理员PIN
        "maxRetryCount":""      --- 管理员PIN最大重试次数
    }
    "user":{
        "PIN":"",               --- 用户PIN
        "maxRetryCount":""      --- 用户PIN最大重试次数
    }
    "fileRight":""              --- 创建文件和容器的权限
}
*/
std::string buildCreateAppReq(const std::string& devID, const std::string& appName, const std::string& admin_PIN, const uint32& admin_maxRetryCount,
                              const std::string& user_PIN, const uint32& user_maxRetryCount, const uint32& fileRight);

/**
jsonBody =
{
    "devID":"",       ---  设备ID
    "appName":"",     ---  应用名称
}
*/
std::string buildDelAppReq(const std::string& devID, const std::string& appName);

/**
jsonBody =
{
    "devID":"",      ---  设备ID
    "appName":""     ---  应用名称
}
*/
std::string buildGetContainersReq(const std::string& devID, const std::string& appName);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "containerName":"",         --- 容器名称
}
*/
std::string buildCreateContainerReq(const std::string& devID, const std::string& appName, const std::string& containerName);

/**
jsonBody =
{
    "devID":"",         ---  设备ID
    "appName":"",       ---  应用名称
    "containerName":""  ---  容器名称
}
*/
std::string buildDelContainerReq(const std::string& devID, const std::string& appName, const std::string& containerName);

/**
jsonBody =
{
    "devID":"",         ---  设备ID
    "appName":"",       ---  应用名称
    "containerName":""  ---  容器名称
}
*/
std::string buildGetContainerTypeReq(const std::string& devID, const std::string& appName, const std::string& containerName);

/**
jsonBody =
{
    "devID":"",             ---  设备ID
    "appName":"",           ---  应用名称
    "containerName":"",     ---  容器名称
    "signFlag":"",          ---  1表示签名证书，0表示加密证书
    "cert":"",              ---  证书内容缓冲区
    "certLen":""            ---  证书长度
}
*/
std::string buildImportCertificateReq(const std::string& devID, const std::string& appName, const std::string& containerName, const uint32& signFlag,
                                      const std::string& cert);

/**
jsonBody =
{
    "devID":"",             ---  设备ID
    "appName":"",           ---  应用名称
    "containerName":"",     ---  容器名称
    "signFlag":"",          ---  1表示签名证书，0表示加密证书
}
*/
std::string buildExportCertificatReq(const std::string& devID, const std::string& appName, const std::string& containerName, const uint32& signFlag);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "containerName":"",         --- 容器名称
    "signFlag":""               --- 1表示签名公钥, 0表示加密公钥
}
*/
std::string buildExportPublicKeyReq(const std::string& devID, const std::string& appName, const std::string& containerName, const uint32& signFlag);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "pubKey":""，               --- 公钥(base64编码)
        "type":"",                  --- 1表示RSA,2表示ECC
        "srcData":""                --- 源数据(base64编码)
}
*/
std::string buildExtPubKeyEncryptReq(const std::string& devID, const std::string& pubKey, const uint32& type, const std::string& srcData);

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "priKey":""，               --- 私钥(base64编码)
    "type":"",                  --- 1表示RSA,2表示ECC
    "encryptData":""            --- 加密数据(base64编码)
}
*/
std::string buildExtPriKeyDecryptReq(const std::string& devID, const std::string& PriKey, const uint32& Type, const std::string& EncryptData);

/**
jsonBody =
{
    "name":"",                  --- provider名称
    "PIDVID":[
                "",
    ]                           --- PIDVID
}
*/
std::string buildSetProviderReq(const std::string& name, const std::string& VPID);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "type":"",                  --- 指纹类型
}
*/
std::string buildUnblockFingerReq(const std::string& devID, const std::string& appName, const uint32& type);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "type":"",                  --- 指纹类型
}
*/
std::string buildInitFingerReq(const std::string& devID, const uint32& type);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "type":"",                  --- 指纹类型
}
*/
std::string buildHasFingerReq(const std::string& devID, const std::string& appName, const uint32& type);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "type":"",                  --- 指纹类型
}
*/
std::string buildVerifyFingerReq(const std::string& devID, const std::string& appName, const uint32& type);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
}
*/
std::string buildCancleFingerReq(const std::string& devID);

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":"",              --- 文件名
    "fileSize":"",              --- 文件大小
    "readRights":"",            --- 读权限（十进制）
    "writeRight":""             --- 写权限（十进制）
}
*/
std::string buildCreateFileReq(const std::string& devId, const std::string& appName, const std::string& fileName, const unsigned int& fileSize,
                               const unsigned int& readRights, const unsigned int& writeRights);

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":""               --- 文件名
}
*/
std::string buildDeleteFileReq(const std::string& devId, const std::string& appName, const std::string& fileName);

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
}
*/
std::string buildGetFileListReq(const std::string& devId, const std::string& appName);

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":""               --- 文件名
}
*/
std::string buildGetFileInfoReq(const std::string& devId, const std::string& appName, const std::string& fileName);

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":"",              --- 文件名
    "offset":"",                --- 文件读取偏移位置
    "size":""                   --- 要读取的长度
}
*/
std::string buildReadFileReq(const std::string& devId, const std::string& appName, const std::string& fileName, const unsigned int& offset,
                             const unsigned int& size);

/*
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":"",              --- 文件名
    "offset":"",                --- 写入文件的偏移量
    "data":""                   --- 数据
}
*/
std::string buildWriteFileReq(const std::string& devId, const std::string& appName, const std::string& fileName, const unsigned int& offset,
                              const std::string& data);

/*
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "dn":"",                --- DN项
        "extType":""			--- 临时密钥扩展项，1.不携带，2.携带临时密钥，其他值.协同模式。
}
*/
std::string buildMakePkcs10Req(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& dn,
                               const int& extensionType, const int& reqDigst);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "keyType":"",           --- 0表示SM2, 1表示RSA
    "keyLen":""             --- keyType为RSA时候有效
    "purpose":""            --- keyType为SM2时候有效(目前key不支持2),1. SGD_SM2_1  2. SGD_SM2_3
}
*/
std::string buildGenb64KeypairReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& keyType,
                                  const std::string& keyLen, const uint32& purpose);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "b64Key":""             --- 密钥对(base64编码)
}
*/
std::string buildImportEncReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& b64Key);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "b64cert":""            --- 证书内容(base64编码)
    "purpose":""            --- 1表示签名, 0表示加密
}
*/
std::string buildInstallCertReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& b64cert,
                                const std::string& purpose);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "signFlag":""           --- 0:加密 1:签名
    "certPass":""           --- 密码
    "b64cert":""            --- 证书内容(base64编码)

}
*/
std::string buildImportPfx2SkfFileReq(const std::string& devID, const std::string& appName, const std::string& conName, unsigned int signFlag,
                                      const std::string& certPass, const std::string& b64cert);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "b64cert":""            --- 证书内容(base64编码)
    "certPass":""           --- 密码
}
*/
std::string buildImportPfxReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& b64cert,
                              const std::string& certPass);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "certType","",          --- 证书类型,1表示签名, 0表示加密
}
*/
std::string buildGetb64certReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& certType);

/**
jsonBody =
{
        "devID":"",             --- 设备ID
        "appName":"",           --- 应用名称
        "conName":"",           --- 容器名称
        "srcData":"",           --- 源数据(base64编码)
        "isBase64SrcData":"",   --- 是否为base64编码源数据，1表示是，0表示否
        "type":""               --- 1表示PM-BD签名,2表示SM2/RSA签名
}
*/
std::string buildSignDataReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& srcData,
                             const uint32& isBase64SrcData, const std::string& type);

/**
jsonBody =
{
        "b64signData":""        --- 签名数据(base64编码)
}
*/
bool parseSignDatResp(const std::string& strSrc, std::string& b64signData);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "srcData":"",           --- 源数据
    "signData":"",          --- 签名数据
        "isBase64SrcData":"",   --- 是否为base64编码源数据，1表示是，0表示否
    "type":""               --- 1表示PM-BD验签,2表示SM2/RSA验签
}
*/
std::string buildVerifyDataReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& srcData,
                               const std::string& signData, const uint32& isBase64SrcData, const uint32& type);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "srcData":"",           --- 待签数据(base64编码)
    "attachData":""         --- 0表示detached方式签名
    "mdType":""             --- 指定的摘要类型，"1"-MD5 "2"-SHA1 "3"-SM3 "4"-SHA256
}
*/
std::string buildPkcs7SignReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& srcData,
                              const uint32& mdType, const std::string& attachData, const uint32& signwithSM2Std, const uint32& noAttr);
/**
jsonBody =
{
        "signData":""        --- 签名数据(base64编码)
}
*/
bool parsePkcs7SignResp(const std::string& strSrc, std::string& signData);

/**
jsonBody =
{
    "srcData":"",           --- 源数据
    "signData":""           --- 签名数据
}
*/
std::string buildPkcs7VerifyReq(const std::string& srcData, const std::string& signData);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "pubkey":"",            --- 公钥(base64编码)
    "srcData":"",           --- 源数据(base64编码)
    "signData":""           --- 签名数据(base64编码)
}
*/
std::string buildExPubVerifyReq(const std::string& devID, const std::string& pubkey, const std::string& srcData, const std::string& signData);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "cert":"",              --- 证书(base64编码)
    "srcData":"",           --- 源数据(base64编码)
    "signData":""           --- 签名数据(base64编码)
}
*/
std::string buildExCertVerifyReq(const std::string& devID, const std::string& b64cert, const std::string& srcData, const std::string& signData);

/**
jsonBody =
{
    "devID":"",             --- 设备
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "signFlag":""           --- 1表示签名证书, 0表示加密证书
}
*/
std::string buildDupb64certWithTemplateReq(const std::string& devID, const std::string& appName, const std::string& conName,
                                           const std::string& signFlag);

/**
jsonBody =
{
    "cert":"",              --- 证书内容(base64编码)
}
*/
std::string buildCertParseReq(const std::string& cert);

/**
jsonBody =
{
    "srcData":"",           --- 明文数据(base64编码)
    "cert":"",              --- 证书(base64编码)
    "cihperType":""         --- 指定的算法类型，"0"-DES "1"-3DES "2"-AES "3"-SM4
}
*/
std::string buildEnvelopeEncryptReq(const std::string& srcData, const std::string& cert, const uint32& cihperType);

/**
jsonBody =
{
        "envelopeData":"",                --- 数字信封(base64编码)
}
*/
bool parseEnvelopeEncryptResp(const std::string& strSrc, std::string& envelopeData);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "srcData":"",           --- 信封数据(base64编码)
}
*/
std::string buildEnvelopeDecryptReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& srcData);

/**
jsonBody =
{
    "srcData":"",           --- 源数据
    "signData":""           --- 签名数据
    "cert":""               --- 证书
}
*/
std::string buildVerifySignedMessageReq(const std::string& srcData, const std::string& signData, const std::string& cert);

/**
jsonBody =
{
    "cert":"",              --- 证书内容（base64编码）
    "oid":""                --- 获取证书扩展项的oid
}
*/
std::string buildGetExtensionReq(const std::string& cert, const std::string& oid);

/* kmail */
/**
jsonBody =
{
        "body":""                   --- 文本主体
}
*/
std::string buildSetTextBodyReq(const std::string& body);

/**
jsonBody =
{
        "body":""                   --- 超文本主体
}
*/
std::string buildSetHtmlBodyReq(const std::string& body);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "conName":"",               --- 容器名称
}
*/
std::string buildComposeReq(const std::string& devID, const std::string& appName, const std::string& conName);

/**
jsonBody =
{
        "index":""                  --- 索引
}
*/
std::string buildGetComposedDataReq(const unsigned int& index);

/**
jsonBody =
{
        "index":"",                 --- 索引(当index为0时为第一包数据)
        "mail":""                   --- 邮件内容
}
*/
std::string buildPrepareParseReq(const unsigned int& index, const std::string& mail);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "conName":"",               --- 容器名称
}
*/
std::string buildParseReq(const std::string& devID, const std::string& appName, const std::string& conName);

/**
jsonBody =
{
        "fileInfo":""               --- 附件信息，fileInfo为Json格式（名称、附件头（密级......））
                                                                --- {fileName:xxx, fileSize:xxx, extFields:[xxx, xxx]}
}
*/
std::string buildAddAttachFileReq(const std::string& fileInfo);

/**
jsonBody =
{
        "index":""                  --- 索引
}
*/
std::string buildGetAttachFileInfoReq(const unsigned int& index);

/**
jsonBody =
{
        "index":"",                 --- 索引
        "filePath":""               --- 文件路径
}
*/
std::string buildDoAttachSaveAsReq(const unsigned int& index, const std::string& filePath);

/**
jsonBody =
{
        "index":""                  --- 索引
}
*/
std::string buildGetAttachFieldInfoReq(const unsigned int& index);

/**
jsonBody =
{
        "type":""                   --- 邮件类型，1：加密；2：签名
}
*/
std::string buildSetMailTypeReq(const unsigned int& type);

/**
jsonBody =
{
        "encCert":""                --- 证书内容
}
*/
std::string buildSetEncCerts(const std::string& encCert);

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "conName":""                --- 容器名称
}
*/
std::string buildGetSignCertReq(const std::string& devID, const std::string& appName, const std::string& conName);

/**
jsonBody =
{
        "certb64":"",               --- 证书内容
        "key":""                    --- 指定项：sn或cn
}
*/
std::string buildGetCertItemReq(const std::string& certb64, const std::string& key);

/**
jsonBody =
{
        "devID":"",             --- 设备ID
        "appName":"",           --- 应用名称
        "conName":"",           --- 容器名称
        "srcData","",           --- 待签名数据
        "type":""               --- 1表示PM-BD签名,2表示SM2/RSA签名
}
*/
std::string buildSignDataReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& srcData,
                             const unsigned int& type);

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "RandomLen":""          --- 随机数字节数
}
*/
std::string buildGenRandomReq(const std::string& devID, const unsigned int& randomLen);

/**
jsonBody =
{
    "label":""             --- 认证协议标签
}
*/
std::string buildAuthInitReq(const std::string& label);
/**
jsonBody =
{
    "label":""             --- 认证协议标签
    "authType":""          --- 认证类型
}
*/
std::string buildUnionAuthReq(const std::string& label, const unsigned int authType, const std::string& contend);

/**
jsonBody =
{
    "label":""             --- 认证协议标签
    "authType":""          --- 认证类型
    "appNo":""             --- 应用标识
}
*/
std::string buildAppTokenAuthReq(const std::string& label, const unsigned int authType, const std::string& contend, const std::string& appNo);

/**
jsonBody =
{
        "label": "",           --- 认证协议标签
        "loginUIProvider": 1,  --- 认证界面方式：0--应用认证界面 1--认证服务认证界面
        "authType": 0,         --- 认证类型
        "aapID": "",           --- 应用id
        "domainID": "",        --- 域标识
        "Random": "",          --- 随机数
        "reserved": "",        --- 保留
        "contend": ""          --- 接口描述
}
*/
std::string buildGetTokenReq(const unsigned int loginFlag, const std::string& label, const unsigned int authType, const std::string& appId,
                             const std::string& domainId, const std::string& Random, const std::string& reserved, const std::string& contend);

/**
jsonBody =
{
        "label": "",           --- 认证协议标签
        "loginUIProvider": 1,  --- 认证界面方式：0--应用启动认证界面 1--认证服务启动认证界面
        "authType": 0,         --- 认证类型
        "aapID": "",           --- 应用id
        "domainID": "",        --- 域标识
        "Random": "",          --- 随机数
        "reserved": "",        --- 保留
        "contend": ""          --- 接口描述
    "userNmae": "",        --- 用户名
        "passWd": ""           --- 密码
}
*/
std::string buildGetTokenReqByPwd(const unsigned int loginFlag, const std::string& label, const unsigned int authType, const std::string& appId,
                                  const std::string& domainId, const std::string& Random, const std::string& reserved, const std::string& contend,
                                  const std::string& userNmae, const std::string& passWd);

std::string buildSetTrustedDrivesReqArray();

std::string buildSetTrustedDrivesReq(const std::string& drives);

JSON_Value* buildDrivesOBJ(const std::string& name, const std::string& path, const std::string& hash, const std::string& comment);

}  // namespace testAgent
}  // namespace koal

#endif