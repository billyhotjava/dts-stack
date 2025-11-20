#include "jsonProtocol.h"

namespace koal {
namespace testAgent {

/**
   请求错误回复
 */
std::string buildreqErrorResp(const std::string& err){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("msg", err) SAFE_BUILD_JSON_END()}

/*## 获取中间件初始化状态  path="koalService/getInitStat"
msgType = 0x00 ;  ///请求类型

### 请求:
```
version = 0x01 ;  ///消息版本  body json模板的版本
extend  = 0x0 ;
```

*/

std::string buildGetInitStatResp(unsigned int nInitStat) {
    SAFE_BUILD_JSON_BEGIN()
    SAFE_SET_JSON_STRING2I32("initStat", nInitStat)
    SAFE_BUILD_JSON_END()
}

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
bool parseLoginReq(const std::string& strSrc, std::string& appName, std::string& appID, std::string& token) {
    SAFE_PARSE_JSON_BEGIN()
    SAFE_GET_JSON_STRING("appName", appName)
    SAFE_GET_JSON_STRING("appID", appID)
    SAFE_GET_JSON_STRING("token", token)
    SAFE_PARSE_JSON_END()
    return true;
}
/**
 *  "sessionID":"",
    "ticket":"",
    "notifyPort":"", --- 推送使用的端口
    "timeout":"",  --- session过期时间,单位秒
 */
std::string buildLoginResp(int64 sessionID, std::string& ticket, int32 notifyerPort, int32 timeOut) {
    SAFE_BUILD_JSON_BEGIN()
    SAFE_SET_JSON_STRING2I64("sessionID", sessionID)
    SAFE_SET_JSON_STRING("ticket", ticket)
    SAFE_SET_JSON_STRING2I32("notifyPort", notifyerPort)
    SAFE_SET_JSON_STRING2I32("timeout", timeOut)
    SAFE_BUILD_JSON_END()
}

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
bool parseLogOutReq(const std::string& strSrc, int64& sessionID, std::string& ticket) {
    SAFE_PARSE_JSON_BEGIN()
    SAFE_GET_JSON_STRING2I64("sessionID", sessionID)
    SAFE_GET_JSON_STRING("ticket", ticket)
    SAFE_PARSE_JSON_END()
    return true;
}

/**
 * jsonBody =
{
    "devID":"",         ---  系统自定义的设备的编号
    "devNumber":"",     ---  设备编号，设备自带
    "devLable":""       ---  设备标签, 可以用户设置
}
 */
std::string buildDeviceInResp(const std::string& devID, const std::string& devNumber, const std::string& devLable){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("devNumber", devNumber)
        SAFE_SET_JSON_STRING("devLable", devLable) SAFE_BUILD_JSON_END()}  ///拔出
std::string buildDeviceOutResp(const std::string& devID, const std::string& devNumber, const std::string& devLable) {
    SAFE_BUILD_JSON_BEGIN()
    SAFE_SET_JSON_STRING("devID", devID)
    SAFE_SET_JSON_STRING("devNumber", devNumber)
    SAFE_SET_JSON_STRING("devLable", devLable)
    SAFE_BUILD_JSON_END()
}

bool parseGetDevicesResponse(const std::string& strSrc, std::vector<std::map<std::string, std::string> >& array) {
    JSON_Value* pJson = json_parse_string(strSrc.c_str());
    if (pJson == NULL) {
        return false;
    }
    JSON_Object* rootObject = json_value_get_object(pJson);
    if (rootObject == NULL) {
        json_value_free(pJson);
        return false;
    }

    JSON_Array* pArray = NULL;
    pArray = json_object_get_array(rootObject, "devices");
    if (!pArray) {
        json_value_free(pJson);
        return false;
    }

    for (int i = 0; i < json_array_get_count(pArray); i++) {
        std::map<std::string, std::string> mapTemp;
        int type = json_type(json_array_get_value(pArray, i));
        if (JSONObject == type || JSONArray == type) {
            JSON_Object* item = json_array_get_object(pArray, i);
            for (int j = 0; j < json_object_get_count(item); j++) {
                const char* keyName = json_object_get_name(item, j);
                mapTemp[keyName] = json_object_get_string(item, keyName);
            }
        }
        array.push_back(mapTemp);
    }
    return true;
}

/**
jsonBody =
{
    "devID":""                  --- 设备ID
}
*/
std::string buildGetDevInfoReq(const std::string& devID){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "lable":""                  --- 标签
}
*/
std::string buildSetDevLableReq(const std::string& devID, const std::string& lable){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID)
                                                                                        SAFE_SET_JSON_STRING("lable", lable) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "command":""                --- 命令二进制数据的base64编码
}
*/
std::string buildTransMitDataReq(const std::string& devID, const std::string& command){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("command", command) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "authData":""               --- 认证二进制数据的base64编码
}
*/
std::string buildDevAuthReq(const std::string& devID, const std::string& authData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("authData", authData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "authKeyData":""            --- 认证二进制数据的base64编码
}
*/
std::string buildChangeAuthKeyReq(const std::string& devID, const std::string& authKeyData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("authKeyData", authKeyData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":""                --- PIN类型
}
*/
std::string buildGetPINInfoReq(const std::string& devID, const std::string& appName, const uint32& PINType){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("PINType", PINType)
        SAFE_BUILD_JSON_END()}

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
                              const std::string& newPIN){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("PINType", PINType)
        SAFE_SET_JSON_STRING("oldPIN", oldPIN) SAFE_SET_JSON_STRING("newPIN", newPIN) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":"",               --- PIN类型
    "PIN"    :""                --- PIN码
}
*/
std::string buildVerifyPINReq(const std::string& devID, const std::string& appName, const uint32& PINType, const std::string& PIN){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("PINType", PINType)
        SAFE_SET_JSON_STRING("PIN", PIN) SAFE_SET_JSON_STRING2I32("isCachedPIN", 0) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "PINType":""                --- PIN类型
}
*/
std::string buildGetCachedPINReq(const std::string& devID, const std::string& appName, const uint32& PINType){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("PINType", PINType)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "adminPIN":"",              --- 管理员PIN码
    "userPIN":""                --- 用户PIN码
}
*/
std::string buildUnlockPINReq(const std::string& devID, const std::string& appName, const std::string& adminPIN, const std::string& userPIN){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("adminPIN", adminPIN)
        SAFE_SET_JSON_STRING("userPIN", userPIN) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":""                  --- 设备ID
}
*/
std::string buildGetAppListReq(const std::string& devID){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_BUILD_JSON_END()}

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
                              const std::string& user_PIN, const uint32& user_maxRetryCount, const uint32& fileRight){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_DOTSET_JSON_STRING("admin.PIN", admin_PIN) SAFE_DOTSET_JSON_STRING2I32("admin.maxRetryCount", admin_maxRetryCount)
            SAFE_DOTSET_JSON_STRING("user.PIN", user_PIN) SAFE_DOTSET_JSON_STRING2I32("user.maxRetryCount", user_maxRetryCount)
                SAFE_SET_JSON_STRING2I32("fileRight", fileRight) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",       ---  设备ID
    "appName":"",     ---  应用名称
}
*/
std::string buildDelAppReq(const std::string& devID, const std::string& appName){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID)
                                                                                     SAFE_SET_JSON_STRING("appName", appName) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",      ---  设备ID
    "appName":""     ---  应用名称
}
*/
std::string buildGetContainersReq(const std::string& devID, const std::string& appName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "containerName":"",         --- 容器名称
}
*/
std::string buildCreateContainerReq(const std::string& devID, const std::string& appName, const std::string& containerName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_SET_JSON_STRING("containerName", containerName) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",         ---  设备ID
    "appName":"",       ---  应用名称
    "containerName":""  ---  容器名称
}
*/
std::string buildDelContainerReq(const std::string& devID, const std::string& appName, const std::string& containerName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_SET_JSON_STRING("containerName", containerName) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",         ---  设备ID
    "appName":"",       ---  应用名称
    "containerName":""  ---  容器名称
}
*/
std::string buildGetContainerTypeReq(const std::string& devID, const std::string& appName, const std::string& containerName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_SET_JSON_STRING("containerName", containerName) SAFE_BUILD_JSON_END()}

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
                                      const std::string& cert){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_SET_JSON_STRING("containerName", containerName) SAFE_SET_JSON_STRING2I32("signFlag", signFlag) SAFE_SET_JSON_STRING("cert", cert)
            SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             ---  设备ID
    "appName":"",           ---  应用名称
    "containerName":"",     ---  容器名称
    "signFlag":"",          ---  1表示签名证书，0表示加密证书
}
*/
std::string buildExportCertificatReq(const std::string& devID, const std::string& appName, const std::string& containerName, const uint32& signFlag){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_SET_JSON_STRING("containerName", containerName) SAFE_SET_JSON_STRING2I32("signFlag", signFlag) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "containerName":"",         --- 容器名称
    "signFlag":""               --- 1表示签名公钥, 0表示加密公钥
}
*/
std::string buildExportPublicKeyReq(const std::string& devID, const std::string& appName, const std::string& containerName, const uint32& signFlag){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName)
        SAFE_SET_JSON_STRING("containerName", containerName) SAFE_SET_JSON_STRING2I32("signFlag", signFlag) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "pubKey":""，               --- 公钥(base64编码)
        "type":"",                  --- 1表示RSA,2表示ECC
        "srcData":""                --- 源数据(base64编码)
}
*/
std::string buildExtPubKeyEncryptReq(const std::string& devID, const std::string& pubKey, const uint32& type, const std::string& srcData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("pubKey", pubKey) SAFE_SET_JSON_STRING2I32("type", type)
        SAFE_SET_JSON_STRING("srcData", srcData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",                 --- 设备ID
    "priKey":""，               --- 私钥(base64编码)
    "type":"",                  --- 1表示RSA,2表示ECC
    "encryptData":""            --- 加密数据(base64编码)
}
*/
std::string buildExtPriKeyDecryptReq(const std::string& devID, const std::string& PriKey, const uint32& Type, const std::string& EncryptData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("priKey", PriKey) SAFE_SET_JSON_STRING2I32("type", Type)
        SAFE_SET_JSON_STRING("encryptData", EncryptData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "name":"",                  --- provider名称
    "PIDVID":[
                "",
    ]                           --- PIDVID
}
*/
std::string buildSetProviderReq(const std::string& name, const std::string& VPID){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("name", name)
                                                                                      SAFE_DOSET_JSON_VALUE("PIDVID", VPID) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "type":"",                  --- 指纹类型
}
*/
std::string buildUnblockFingerReq(const std::string& devID, const std::string& appName, const uint32& type){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("type", type)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "type":"",                  --- 指纹类型
}
*/
std::string buildInitFingerReq(const std::string& devID, const uint32& type){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID)
                                                                                 SAFE_SET_JSON_STRING2I32("type", type) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "type":"",                  --- 指纹类型
}
*/
std::string buildHasFingerReq(const std::string& devID, const std::string& appName, const uint32& type){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("type", type)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "type":"",                  --- 指纹类型
}
*/
std::string buildVerifyFingerReq(const std::string& devID, const std::string& appName, const uint32& type){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING2I32("type", type)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
}
*/
std::string buildCancleFingerReq(const std::string& devID){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_BUILD_JSON_END()}

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
                               const unsigned int& readRights, const unsigned int& writeRights){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devId) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("fileName", fileName)
        SAFE_SET_JSON_STRING2I32("fileSize", fileSize) SAFE_SET_JSON_STRING2I32("readRights", readRights)
            SAFE_SET_JSON_STRING2I32("writeRights", writeRights) SAFE_BUILD_JSON_END()}

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":""               --- 文件名
}
*/
std::string buildDeleteFileReq(const std::string& devId, const std::string& appName, const std::string& fileName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devId) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("fileName", fileName)
        SAFE_BUILD_JSON_END()}

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
}
*/
std::string buildGetFileListReq(const std::string& devId, const std::string& appName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devId) SAFE_SET_JSON_STRING("appName", appName) SAFE_BUILD_JSON_END()}

/**
{
    "devID":"",                 --- 设备ID
    "appName":"",               --- 应用名称
    "fileName":""               --- 文件名
}
*/
std::string buildGetFileInfoReq(const std::string& devId, const std::string& appName, const std::string& fileName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devId) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("fileName", fileName)
        SAFE_BUILD_JSON_END()}

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
                             const unsigned int& size){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devId) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("fileName", fileName)
        SAFE_SET_JSON_STRING2I32("offset", offset) SAFE_SET_JSON_STRING2I32("size", size) SAFE_BUILD_JSON_END()}

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
                              const std::string& data){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devId) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("fileName", fileName)
        SAFE_SET_JSON_STRING2I32("offset", offset) SAFE_SET_JSON_STRING("data", data) SAFE_BUILD_JSON_END()}

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
                               const int& extensionType, const int& reqDigst){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("dn", dn) SAFE_SET_JSON_STRING2I32("extType", extensionType) SAFE_SET_JSON_STRING2I32("reqDigst", reqDigst)
            SAFE_BUILD_JSON_END()}

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
                                  const std::string& keyLen, const uint32& purpose){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("keyType", keyType) SAFE_SET_JSON_STRING("keyLen", keyLen) SAFE_SET_JSON_STRING2I32("purpose", purpose)
            SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "b64Key":""             --- 密钥对(base64编码)
}
*/
std::string buildImportEncReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& b64Key){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("b64Key", b64Key) SAFE_BUILD_JSON_END()}

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
                                const std::string& purpose){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("b64cert", b64cert) SAFE_SET_JSON_STRING("purpose", purpose) SAFE_BUILD_JSON_END()}

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
                                      const std::string& certPass, const std::string& b64cert){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING2I32("signFlag", signFlag) SAFE_SET_JSON_STRING("passWD", certPass) SAFE_SET_JSON_STRING("cert", b64cert)
            SAFE_BUILD_JSON_END()}

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
                              const std::string& certPass){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("b64cert", b64cert) SAFE_SET_JSON_STRING("certPass", certPass) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "certType","",          --- 证书类型,1表示签名, 0表示加密
}
*/
std::string buildGetb64certReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& certType){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("certType", certType) SAFE_BUILD_JSON_END()}

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
                             const uint32& isBase64SrcData, const std::string& type) {
    SAFE_BUILD_JSON_BEGIN()
    SAFE_SET_JSON_STRING("devID", devID)
    SAFE_SET_JSON_STRING("appName", appName)
    SAFE_SET_JSON_STRING("conName", conName)
    SAFE_SET_JSON_STRING("srcData", srcData)
    SAFE_SET_JSON_STRING2I32("isBase64SrcData", isBase64SrcData)
    SAFE_SET_JSON_STRING("type", type)
    SAFE_BUILD_JSON_END()
}
/**
jsonBody =
{
        "b64signData":""        --- 签名数据(base64编码)
}
*/
bool parseSignDatResp(const std::string& strSrc,
                      std::string& b64signData){SAFE_PARSE_JSON_BEGIN() SAFE_GET_JSON_STRING("b64signData", b64signData) SAFE_PARSE_JSON_END()}

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
                               const std::string& signData, const uint32& isBase64SrcData, const uint32& type){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("srcData", srcData) SAFE_SET_JSON_STRING("signData", signData)
            SAFE_SET_JSON_STRING2I32("isBase64SrcData", isBase64SrcData) SAFE_SET_JSON_STRING2I32("type", type) SAFE_BUILD_JSON_END()}

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
                              const uint32& mdType, const std::string& attachData, const uint32& signwithSM2Std, const uint32& noAttr) {
    SAFE_BUILD_JSON_BEGIN()
    SAFE_SET_JSON_STRING("devID", devID)
    SAFE_SET_JSON_STRING("appName", appName)
    SAFE_SET_JSON_STRING("conName", conName)
    SAFE_SET_JSON_STRING("srcData", srcData)
    SAFE_SET_JSON_STRING2I32("mdType", mdType)
    SAFE_SET_JSON_STRING("attachData", attachData)
    SAFE_SET_JSON_STRING2I32("signwithSM2Std", signwithSM2Std)
    SAFE_SET_JSON_STRING2I32("noAttr", noAttr)
    SAFE_BUILD_JSON_END()
}
/**
jsonBody =
{
        "signData":""        --- 签名数据(base64编码)
}
*/
bool parsePkcs7SignResp(const std::string& strSrc,
                        std::string& signData){SAFE_PARSE_JSON_BEGIN() SAFE_GET_JSON_STRING("signData", signData) SAFE_PARSE_JSON_END()}

/**
jsonBody =
{
    "srcData":"",           --- 源数据
    "signData":""           --- 签名数据
}
*/
std::string buildPkcs7VerifyReq(const std::string& srcData, const std::string& signData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("srcData", srcData) SAFE_SET_JSON_STRING("signData", signData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "pubkey":"",            --- 公钥(base64编码)
    "srcData":"",           --- 源数据(base64编码)
    "signData":""           --- 签名数据(base64编码)
}
*/
std::string buildExPubVerifyReq(const std::string& devID, const std::string& pubkey, const std::string& srcData, const std::string& signData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("pubkey", pubkey) SAFE_SET_JSON_STRING("srcData", srcData)
        SAFE_SET_JSON_STRING("signData", signData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "cert":"",              --- 证书(base64编码)
    "srcData":"",           --- 源数据(base64编码)
    "signData":""           --- 签名数据(base64编码)
}
*/
std::string buildExCertVerifyReq(const std::string& devID, const std::string& b64cert, const std::string& srcData, const std::string& signData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("cert", b64cert) SAFE_SET_JSON_STRING("srcData", srcData)
        SAFE_SET_JSON_STRING("signData", signData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "signFlag":""           --- 1表示签名证书, 0表示加密证书
}
*/
std::string
    buildDupb64certWithTemplateReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& signFlag){
        SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
            SAFE_SET_JSON_STRING("signFlag", signFlag) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "cert":"",              --- 证书内容(base64编码)
}
*/
std::string buildCertParseReq(const std::string& cert){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("cert", cert) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "srcData":"",           --- 明文数据(base64编码)
    "cert":"",              --- 证书(base64编码)
    "cihperType":""         --- 指定的算法类型，"0"-DES "1"-3DES "2"-AES "3"-SM4
}
*/
std::string buildEnvelopeEncryptReq(const std::string& srcData, const std::string& cert, const uint32& cihperType) {
    SAFE_BUILD_JSON_BEGIN()
    SAFE_SET_JSON_STRING("srcData", srcData)
    SAFE_SET_JSON_STRING("cert", cert)
    SAFE_SET_JSON_STRING2I32("cihperType", cihperType)
    SAFE_BUILD_JSON_END()
}
/**
jsonBody =
{
        "envelopeData":"",                --- 数字信封(base64编码)
}
*/
bool parseEnvelopeEncryptResp(const std::string& strSrc, std::string& envelopeData){
    SAFE_PARSE_JSON_BEGIN() SAFE_GET_JSON_STRING("envelopeData", envelopeData) SAFE_PARSE_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "srcData":"",           --- 信封数据(base64编码)
}
*/
std::string buildEnvelopeDecryptReq(const std::string& devID, const std::string& appName, const std::string& conName, const std::string& srcData){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("srcData", srcData) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "appName":"",           --- 应用名称
    "conName":"",           --- 容器名称
    "srcData":"",           --- 源数据
    "signData":""           --- 签名数据
    "cert":""               --- 证书
}
*/
std::string buildVerifySignedMessageReq(const std::string& srcData, const std::string& signData, const std::string& cert){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("srcData", srcData) SAFE_SET_JSON_STRING("signData", signData) SAFE_SET_JSON_STRING("cert", cert)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "cert":"",              --- 证书内容（base64编码）
    "oid":""                --- 获取证书扩展项的oid
}
*/
std::string buildGetExtensionReq(const std::string& cert, const std::string& oid){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("cert", cert)
                                                                                      SAFE_SET_JSON_STRING("oid", oid) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "body":""                   --- 文本主体
}
*/
std::string buildSetTextBodyReq(const std::string& body){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("body", body) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "body":""                   --- 超文本主体
}
*/
std::string buildSetHtmlBodyReq(const std::string& body){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("body", body) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "conName":"",               --- 容器名称
}
*/
std::string buildComposeReq(const std::string& devID, const std::string& appName, const std::string& conName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "index":""                  --- 索引
}
*/
std::string buildGetComposedDataReq(const unsigned int& index){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING2I32("index", index) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "index":"",                 --- 索引(当index为0时为第一包数据)
        "mail":""                   --- 邮件内容
}
*/
std::string buildPrepareParseReq(const unsigned int& index, const std::string& mail){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING2I32("index", index)
                                                                                         SAFE_SET_JSON_STRING("mail", mail) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "conName":"",               --- 容器名称
}
*/
std::string buildParseReq(const std::string& devID, const std::string& appName, const std::string& conName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "fileInfo":""               --- 附件信息，fileInfo为Json格式（名称、附件头（密级......））
                                                                --- {fileName:xxx, fileSize:xxx, extFields:[xxx, xxx]}
}
*/
std::string
    buildAddAttachFileReq(const std::string& fileInfo){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("fileInfo", fileInfo) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "index":""                  --- 索引
}
*/
std::string
    buildGetAttachFileInfoReq(const unsigned int& index){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING2I32("index", index) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "index":"",                 --- 索引
        "filePath":""               --- 文件路径
}
*/
std::string buildDoAttachSaveAsReq(const unsigned int& index, const std::string& filePath){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING2I32("index", index) SAFE_SET_JSON_STRING("filePath", filePath) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "index":""                  --- 索引
}
*/
std::string
    buildGetAttachFieldInfoReq(const unsigned int& index){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING2I32("index", index) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "type":""                   --- 邮件类型，1：加密；2：签名
}
*/
std::string buildSetMailTypeReq(const unsigned int& type){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING2I32("type", type) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "encCert":""                --- 证书内容
}
*/
std::string buildSetEncCerts(const std::string& encCert){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("encCert", encCert) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "devID":"",                 --- 设备ID
        "appName":"",               --- 应用名称
        "conName":""                --- 容器名称
}
*/
std::string buildGetSignCertReq(const std::string& devID, const std::string& appName, const std::string& conName){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
        "certb64":"",               --- 证书内容
        "key":""                    --- 指定项：sn或cn
}
*/
std::string buildGetCertItemReq(const std::string& certb64, const std::string& key){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("certb64", certb64)
                                                                                        SAFE_SET_JSON_STRING("key", key) SAFE_BUILD_JSON_END()}

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
                             const unsigned int& type){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING("appName", appName) SAFE_SET_JSON_STRING("conName", conName)
        SAFE_SET_JSON_STRING("srcData", srcData) SAFE_SET_JSON_STRING2I32("type", type) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "devID":"",             --- 设备ID
    "randomLen":""          --- 随机数字节数
}
*/
std::string buildGenRandomReq(const std::string& devID, const unsigned int& randomLen){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("devID", devID) SAFE_SET_JSON_STRING2I32("randomLen", randomLen) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "label":""             --- 认证协议标签
}
*/
std::string buildAuthInitReq(const std::string& label){SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("label", label) SAFE_BUILD_JSON_END()}

/**
jsonBody =
{
    "label":""             --- 认证协议标签
    "authType":""          --- 认证类型
}
*/
std::string buildUnionAuthReq(const std::string& label, const unsigned int authType, const std::string& contend){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("label", label) SAFE_SET_JSON_NUMBER("authType", authType) SAFE_SET_JSON_STRING("contend", contend)
        SAFE_BUILD_JSON_END()}

std::string buildAppTokenAuthReq(const std::string& label, const unsigned int authType, const std::string& appNo, const std::string& contend){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_STRING("label", label) SAFE_SET_JSON_NUMBER("authType", authType) SAFE_SET_JSON_STRING("appNo", appNo)
        SAFE_SET_JSON_STRING("contend", contend) SAFE_BUILD_JSON_END()}

std::string buildGetTokenReq(const unsigned int loginFlag, const std::string& label, const unsigned int authType, const std::string& appId,
                             const std::string& domainId, const std::string& Random, const std::string& reserved, const std::string& contend){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_NUMBER("loginUIProvider", authType) SAFE_SET_JSON_STRING("label", label)
        SAFE_SET_JSON_NUMBER("authType", authType) SAFE_SET_JSON_STRING("aapID", appId) SAFE_SET_JSON_STRING("domainID", domainId)
            SAFE_SET_JSON_STRING("Random", Random) SAFE_SET_JSON_STRING("reserved", reserved) SAFE_SET_JSON_STRING("contend", contend)
                SAFE_BUILD_JSON_END()}

std::string buildGetTokenReqByPwd(const unsigned int loginFlag, const std::string& label, const unsigned int authType, const std::string& appId,
                                  const std::string& domainId, const std::string& Random, const std::string& reserved, const std::string& contend,
                                  const std::string& userNmae, const std::string& passWd){
    SAFE_BUILD_JSON_BEGIN() SAFE_SET_JSON_NUMBER("loginUIProvider", authType) SAFE_SET_JSON_STRING("label", label)
        SAFE_SET_JSON_NUMBER("authType", authType) SAFE_SET_JSON_STRING("aapID", appId) SAFE_SET_JSON_STRING("domainID", domainId)
            SAFE_SET_JSON_STRING("Random", Random) SAFE_SET_JSON_STRING("reserved", reserved) SAFE_SET_JSON_STRING("contend", contend)
                SAFE_SET_JSON_STRING("userName", userNmae) SAFE_SET_JSON_STRING("passWd", passWd) SAFE_BUILD_JSON_END()}

std::string buildSetTrustedDrivesReqArray() {
    std::string drives;
    JSON_Value* pJson = NULL;
    JSON_Value* pJson1 = NULL;
    JSON_Value* drives_value = json_value_init_array();
    JSON_Array* drives_array = json_value_get_array(drives_value);

    if (!drives_value || !drives_array) {
        return "";
    }
    pJson = buildDrivesOBJ("WinUKey", "C:\\WINDOWS\\system32\\WTSKFInterface.dll", "2b7f7ccebc9e4ef51c058a34dc22f615181a110d", "windows_x86");
    pJson1 = buildDrivesOBJ("KOAL Key CSP For KOAL V1.0", "C:\\WINDOWS\\system32\\KOALCSP11_s.dll", "b3728ad271b36c3cc8e0b2c77d4ed0bbf830e95e",
                            "windows_x86_x64");
    if (!pJson || !pJson1) {
        return "";
    }
    json_array_append_value(drives_array, pJson);
    json_array_append_value(drives_array, pJson1);
    drives = json_serialize_to_string_pretty(drives_value);
    std::string tmpDrives = buildSetTrustedDrivesReq(drives);
    json_value_free(pJson);
    json_value_free(pJson1);
    return tmpDrives;
}

std::string buildSetTrustedDrivesReq(const std::string& drives){SAFE_BUILD_JSON_BEGIN() SAFE_DOSET_JSON_VALUE("drives", drives) SAFE_BUILD_JSON_END()}

JSON_Value* buildDrivesOBJ(const std::string& name, const std::string& path, const std::string& hash, const std::string& comment) {
    JSON_Value* pJson = json_value_init_object();
    JSON_Object* rootObject = json_value_get_object(pJson);
    if (!pJson || !rootObject) {
        return NULL;
    }

    json_object_set_string(rootObject, "name", name.c_str());
    json_object_set_string(rootObject, "path", path.c_str());
    json_object_set_string(rootObject, "hash", hash.c_str());
    json_object_set_string(rootObject, "comment", comment.c_str());

    return pJson;
}

}  // namespace testAgent
}  // namespace koal