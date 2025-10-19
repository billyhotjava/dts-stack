#ifndef _TESTCASE_HPP_
#define _TESTCASE_HPP_

#include "sync/syncDevice.h"
#include "sync/syncEnroll.h"
#include "sync/syncSignx.h"
#include "sync/syncUnionAuth.h"
#include "sync/syncJDAuth.h"

#define APPNAME "GM6000RSA"
#define CONNAME "4B51EF40-86CC-4A0B-B998-A41EFB400655"  // RSA1024:"FFEB4770-0FDF-4671-9C0C-FF850E0A7C34"
#define PINCODE "123456"

/// doctest
#define DOCTEST_CONFIG_IMPLEMENT
#include "doctest.h"

/// device
koal::testAgent::SyncDevice gDevice;
/// enRoll
koal::testAgent::SyncEnRoll gEnRoll;
/// signX
koal::testAgent::SyncSignX gSignx;
/// unionAuth
koal::testAgent::SyncUnionAuth guniAuth;

std::string gDevId;
std::string gDevType;
int errCode = -1;
bool isKoalSoft;
TEST_CASE("unionAuth") {
    SUBCASE("GetAuthModule") {
        errCode = guniAuth.syncGetAuthModule();
        CHECK(errCode == 0);
    }

    SUBCASE("InitAuth") {
        errCode = guniAuth.syncInitAuth();
        CHECK(errCode == 0);
    }

    SUBCASE("syncGetUserToken") {
        errCode = guniAuth.syncGetUserToken();
        CHECK(errCode == 0);
    }

    SUBCASE("syncRenewUserToken") {
        errCode = guniAuth.syncRenewUserToken();
        CHECK(errCode == 0);
    }

    SUBCASE("syncGetAppToken") {
        errCode = guniAuth.syncGetAppToken();
        CHECK(errCode == 0);
    }

    SUBCASE("syncRenewAppToken") {
        errCode = guniAuth.syncRenewAppToken();
        CHECK(errCode == 0);
    }

    SUBCASE("syncVerifyAppToken") {
        errCode = guniAuth.syncVerifyAppToken();
        CHECK(errCode == 0);
    }

    SUBCASE("syncOfflineAppToken") {
        errCode = guniAuth.syncOfflineAppToken();
        CHECK(errCode == 0);
    }

    SUBCASE("VerifyAuth") {
        errCode = guniAuth.syncVerifyAuth();
        CHECK(errCode == 0);
    }

    SUBCASE("CancleAuth") {
        errCode = guniAuth.syncCancleAuth();
        CHECK(errCode == 0);
    }
}

TEST_CASE("GetDevives") {
    errCode = gDevice.syncGetDevices();
    gDevId = gDevice.syncGetDevID();
    gDevType = gDevice.syncGetDevType();
    if (gDevType == "koal soft")
        isKoalSoft = true;
    else
        isKoalSoft = false;

    CHECK(errCode == 0);
}

// finger test
TEST_CASE("finger test") {
    if (gDevType == "Biocome CSP V2.0") {
        errCode = gDevice.syncHasFinger(gDevId, "EsecuAppNetBankV2", 1);
        CHECK(errCode == 0);

        errCode = gDevice.syncVerifyFinger(gDevId, "EsecuAppNetBankV2", 1);
        CHECK(errCode == 0);
    }
}
//指纹解锁
TEST_CASE("finger test") {
    if (gDevType == "Koal mToken GM3000-HID CSP V1.1--") {
        errCode = gDevice.syncHasFinger(gDevId, "APP", 1);
        CHECK(errCode == 0);

        errCode = gDevice.syncVerifyFinger(gDevId, "APP", 1);
        // CHECK(errCode == 0);

        errCode = gDevice.syncVerifyPIN(gDevId, "APP", 1, "1111111");

        errCode = gDevice.syncUnblockFinger(gDevId, "APP", 1);
        CHECK(errCode == 0);
    }
}

TEST_CASE("GenRandom 256字节") {
    errCode = gDevice.syncGenRandom(gDevId, 256);
    CHECK(errCode == 0);
}

TEST_CASE("GetAllCertBySN") {
    if (isKoalSoft) {
    } else {
        errCode = gDevice.syncGetAllCertBySN();
        CHECK(errCode == 0);
    }
}

TEST_CASE("getDevInfo") {
    errCode = gDevice.syncGetDevInfo(gDevId);
    CHECK(errCode == 0);
}

TEST_CASE("setDevLable") {
    errCode = gDevice.syncSetDevLable(gDevId, "testDevLable");
    CHECK(errCode == 0);
}

///暂不关注此接口
TEST_CASE("transMitData" * doctest::skip(true)) {
    errCode = gDevice.syncTransMitData(gDevId, "");
    CHECK(errCode == 0);
}

TEST_CASE("device Auth") {
    if (isKoalSoft) {
        SUBCASE("devAuth") {
            errCode = gDevice.syncDevAuth(gDevId, "MTIzNDU2NzgxMjM0NTY3OA==");
            CHECK(errCode == 0);
        }
    }
};

TEST_CASE("createApp") {
    if (isKoalSoft) {
        errCode = gDevice.syncCreateApp(gDevId, "app", "admin", 10, "1qaz!QAZ", 10, 255);
        CHECK(errCode == 0);
    }
}

TEST_CASE("getAppList") {
    errCode = gDevice.syncGetAppList(gDevId);
    CHECK(errCode == 0);
}

TEST_CASE("getPINInfo") {
    if (isKoalSoft) {
        errCode = gDevice.syncGetPINInfo(gDevId, "app", 1);
    } else {
        errCode = gDevice.syncGetPINInfo(gDevId, APPNAME, 1);
    }
    CHECK(errCode == 0);
}

TEST_CASE("changePIN") {
    if (isKoalSoft) {
        errCode = gDevice.syncChangePIN(gDevId, "app", 1, "1qaz!QAZ", "123456");
    }
    CHECK(errCode == 0);
}

TEST_CASE("unlockPIN") {
    if (isKoalSoft) {
        errCode = gDevice.syncUnlockPIN(gDevId, "app", "admin", "1qaz!QAZ");
    }

    CHECK(errCode == 0);
}

TEST_CASE("verifyPIN") {
    if (isKoalSoft) {
        errCode = gDevice.syncVerifyPIN(gDevId, "app", 1, "1qaz!QAZ");
    } else {
        errCode = gDevice.syncVerifyPIN(gDevId, APPNAME, 1, PINCODE);
    }
    CHECK(errCode == 0);
}

TEST_CASE("createContainer") {
    std::string strAppName = "";
    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
    }

    errCode = gDevice.syncCreateContainer(gDevId, strAppName, "ECC");
    CHECK(errCode == 0);

    errCode = gDevice.syncCreateContainer(gDevId, strAppName, "RSA");
    CHECK(errCode == 0);
}

TEST_CASE("getContainerType") {
    std::string strAppName = "";
    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
    }

    errCode = gDevice.syncGetContainerType(gDevId, strAppName, "ECC");
    CHECK(errCode == 0);
}

TEST_CASE("getContainers") {
    std::string strAppName = "";
    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
    }

    errCode = gDevice.syncGetContainers(gDevId, strAppName);
    CHECK(errCode == 0);
}

/*
TEST_CASE("GET/SET PROVIDER") {
SUBCASE("getProviders") {
errCode = gDevice.syncGetProviders();
CHECK(errCode == 0);
}

SUBCASE("setProvider") {
std::string PIDVID = "[\"055C_F603\",\"055C_F604\"]";
errCode = gDevice.syncSetProvider("Longmai", PIDVID);
CHECK(errCode == 0);
}
}
*/

TEST_CASE("import/export Certificate") {
    std::string cert =
        "MIICETCCAbigAwIBAgIGIBkFCAAFMAoGCCqBHM9VAYN1MFsxCzAJBgNVBAYTAkNOMQ8wDQYDVQQIDAZzaGFueGkxDTALBgNVBAcMBHhpYW4xDTALBgNVBAoMBGtvYWwxCzAJBgNVBAsM"
        "AmNhMRAwDgYDVQQDDAdlY2NSb290MB4XDTE5MTAyMDEyMjIxNFoXDTI5MTAxNzEyMjIxNFowOzELMAkGA1UEBhMCQ04xDzANBgNVBAgMBnNoYW54aTENMAsGA1UECgwEa29hbDEMMAoG"
        "A1UEAwwDempqMFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEFlQQtdVN0r0EJe9CP1KWHCUWbNwKW0itzGqR9zPB3wkjotxG0ITco9V0zWpsqOLgfDqsUvQw+"
        "YgPyH7fvdIQEqOBhzCBhDAJBgNVHRMEAjAAMAsGA1UdDwQEAwIFIDAqBglghkgBhvhCAQ0EHRYbR21TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBSuRuprzbPKZmtP/"
        "k71BvKn9yxyUTAfBgNVHSMEGDAWgBStNt6lbdm0B8T9oeLvpo84hqmvyTAKBggqgRzPVQGDdQNHADBEAiALsovOfj0FVLEkJ+ZCfCAXeKrenU2NyP1xsYOGysd61wIgHhzR/"
        "iXD43KRCCGUva7lfIcjSE6/fVIUXHT+6++Yg3k=";
    std::string strAppName = "";
    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
    }

    if (isKoalSoft) {
        //先导入签名密钥对,导入签名证书时，skffile会校验签名公钥 和 签名证书的公钥 是否一致
        SUBCASE("importPfx2SkfFile") {
            std::string eccPfxStr =
                "MIIEEgIBAzCCA9gGCSqGSIb3DQEHAaCCA8kEggPFMIIDwTCCArcGCSqGSIb3DQEHBqCCAqgwggKkAgEAMIICnQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIKOPAUKhj"
                "zAgCAggAgIICcFQRSy/C6EvDqMdWJyGKmxvzCIJEqPdQnzc8NqRvxqJc0/2EZymOOpVihv1sl2YzIcPlrjD8m86+72hvGToW+Fv35ZDvoDmml5HKFb/"
                "AhMwe27QwGo73p7v6yTjkLzSz4JdUv6gRTUnJUiVbcJuGN2qS4ByyjEw10vCu6lRKNgOwrv6Krt0U6W+0Bxc78VUpYkxc95jOA/"
                "F6uOaSGZAosLL+biXc8GOZ0meZz92NUqWDkzySFHzi7qL6UcFJ6meT09Nc3K6SeJfKACWlXclNka7wg5pMWrw21cGsRGo3xyTeQME4tFTWWieHFnGvjo/"
                "tuouRPdiuYWMNJj5JcGGPL7uH8w/mnJPaero6DOCrMf8o4p/IrShkH3njNHzeolEu652yHRZnP/hGTSee5/XETRX0CTHrljyx2Lcp7w+dyRC5dIGTwvUk/mTFA5C/"
                "Yf775OwO+"
                "45gEjp6wEMZzs2kNUHj9cLt7D7EEZYJeaZLeSgObsrMgvGaR1xWMkVIcr9l9rpzV2Qtb4svE5uQy3WHrR4MbnQjK9MMTzR2CSnLjKlderqNWT2HFUnp1hXix6bBK7TjWQu+"
                "J+p1KFtr/ss5xahJbyeWIasSqj5Aoduugzq+wMLLBhC/DzXWRgwcVJJ6vtAPSx3nPH2aliKv1VheoIsfza+0jUWR/"
                "kydCCHNyIQ75tXdiqlMqcREj4u13Gjsq83Sn5Mf5iJcp1tMXKFebDgqtHa8BPN+oz0yfTMWZ6eGmr5s0H6fl6fbr+KrFG3UMAoQL7/"
                "0jLXPpXZpmgWROX5QL5iJuOkmAVdQAx+"
                "BDsjhu3v8wqqGZfXG8Xxi7dukZTCCAQIGCSqGSIb3DQEHAaCB9ASB8TCB7jCB6wYLKoZIhvcNAQwKAQKggbQwgbEwHAYKKoZIhvcNAQwBAzAOBAhMkZAWBq4B/"
                "QICCAAEgZAPkzeWCH8ZIRMn1yIGaVMLeGMafQ+ADypKJS/IuPQP58tnvh3udIDJ04ZuH6QsXXzr4xOfBaTHRe/M9ZvKoRkkx7a9PgCaxdo9IN40/"
                "1bqBeOvVNLrHghPVx5rTYhWDUA/+PkEDAnYXvTuObbJr8Lph0le6AHMlDZXtHl7m/"
                "XDPZ2NqVjVEN0dQwotcelCaJExJTAjBgkqhkiG9w0BCRUxFgQU8xfDWkJHSbCMuP6dl1INzjODK/8wMTAhMAkGBSsOAwIaBQAEFNsS6yoBrJXQAIEU/"
                "60AjQn7eAtfBAiFJYY31zOvHwICCAA=";
            errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, strAppName, "ECC", 1, "123456", eccPfxStr);
            CHECK(errCode == 0);

            errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, strAppName, "RSA", 1, "123456", eccPfxStr);
            CHECK(errCode == 0);
        }
    }

    SUBCASE("importCertificate") {
        errCode = gDevice.syncImportCertificate(gDevId, strAppName, "ECC", 1, cert);
        CHECK(errCode == 0);
    }
    SUBCASE("exportCertificate") {
        errCode = gDevice.syncExportCertificate(gDevId, strAppName, "ECC", 1);
        CHECK(errCode == 0);
    }

    SUBCASE("importX509Cert") {
        errCode = gEnRoll.syncImportX509Cert(gDevId, strAppName, "RSA", cert, "1");
        CHECK(errCode == 0);
    }
    SUBCASE("getCert") {
        errCode = gEnRoll.syncGetCert(gDevId, strAppName, "RSA", "1");
        CHECK(errCode == 0);
    }
}

TEST_CASE("GetAllCert") {
    errCode = gDevice.syncGetAllCert();
    CHECK(errCode == 0);
}

TEST_CASE("delContainer") {
    std::string strAppName = "";
    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
    }

    errCode = gDevice.syncDelContainer(gDevId, strAppName, "ECC");
    CHECK(errCode == 0);

    errCode = gDevice.syncDelContainer(gDevId, strAppName, "RSA");
    CHECK(errCode == 0);
}

TEST_CASE("verifyPIN") {
    if (isKoalSoft) {
        errCode = gDevice.syncVerifyPIN(gDevId, "app", 1, "1qaz!QAZ");
    } else {
        errCode = gDevice.syncVerifyPIN(gDevId, APPNAME, 0, PINCODE);
    }
    CHECK(errCode == 0);
}

TEST_CASE("createfile") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
        errCode = gDevice.syncCreateFile(gDevId, strAppName, "test.txt", 256, 16, 16);
        CHECK(errCode == 0);
    }
}

TEST_CASE("getFileInfo") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
        errCode = gDevice.syncGetFileInfo(gDevId, strAppName, "test.txt");
        CHECK(errCode == 0);
    }
}

TEST_CASE("getFileList") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
        errCode = gDevice.syncGetFileList(gDevId, strAppName);
        CHECK(errCode == 0);
    }
}

TEST_CASE("writeFile") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
        errCode = gDevice.syncWriteFile(gDevId, strAppName, "test.txt", 0, "testString");
        CHECK(errCode == 0);
    }
}

TEST_CASE("readFile") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
        errCode = gDevice.syncReadFile(gDevId, strAppName, "test.txt", 0, strlen("testString"));
        CHECK(errCode == 0);
    }
}

TEST_CASE("deletefile") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
    } else {
        strAppName = APPNAME;
        errCode = gDevice.syncDeleteFile(gDevId, strAppName, "test.txt");
        CHECK(errCode == 0);
    }
}

TEST_CASE("delApp") {
    if (isKoalSoft) {
        errCode = gDevice.syncDelApp(gDevId, "app");
    }
    CHECK(errCode == 0);
}

TEST_CASE("EXT Encrypt/Decrypt Data") {
    //原数据为1234567812345678的base64，此接口只支持小数据加解密，必须小于256个字节
    std::string srcData = "MTIzNDU2NzgxMjM0NTY3OAog";

    SUBCASE("ECC") {
        SUBCASE("extPubKeyEncrypt") {
            std::string ECCPubkey =
                "AAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFlQQtdVN0r0EJe9CP1KWHCUWbNwKW0itzGqR9zPB3wkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                "ACOi3EbQhNyj1XTNamyo4uB8OqxS9DD5iA/Ift+90hAS";
            errCode = gDevice.syncExtPubKeyEncrypt(gDevId, ECCPubkey, 2, srcData);
            CHECK(errCode == 0);
        }
        SUBCASE("extPriKeyDecrypt") {
            std::string enECCData =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAArLPjkqy6Pf/QHpOh1PYq2Uvr3YJATY/f1z4qcoHq7BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA3m"
                "+nHcCA+SH5yGoGySgQAQZf7pd7eTanlSiG+1dTg84VEhUBGNS/1gi2z4Yl0D0wT1vGgMDBJwidjBmv2wVDhhIAAAAOKFZEhs+Tc0Zjrmx7T/0UFGsA";
            std::string ECCPrikey = "AAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWm3Fl36vpmkdqhYTOQW5LKpEkrP+7Z4AzI2JG1wOh0Y=";

            errCode = gDevice.syncExtPriKeyDecrypt(gDevId, ECCPrikey, 2, enECCData);
            CHECK(errCode == 0);
        }
    }

    if (isKoalSoft) {
        // skffile 不支持RSA外部公钥加密、外部私钥解密
    } else {
        SUBCASE("RSA") {
            SUBCASE("extPubKeyEncrypt") {
                std::string RSAPubKey =
                    "AAABAAAIAADgY8SKsv2KsM85Wn7qzJGU0VO1EWUvQxob63wJilqit+"
                    "3Iq0tKeuTrg803N48XJG9V17ux9e3FrFfhxpU4F1o7mNJm6QllKxiAT2R7L3T5NggZgdQRoY3e/yah0tjInDmIwI9w4Ie/m+czHZ14KaVJECdpv0G2uNlz0rVrC/"
                    "jYZBxJjDOB+FHJdoQME1QIHgNkLAEFnAm/vqvNGqCcJHpv5QbrGdWpMNEbw9OwprTBW2Odx0Ya2OZU/YDj9Ts2nq6A50VLQVaJmptc4C/"
                    "mc31qI98Ft5fH3Z9pMU+te39CY5ioxJBgIMnHMIBMAdCazFDFbf/K74wZY/xITDLfSeEbAAEAAQ==";

                errCode = gDevice.syncExtPubKeyEncrypt(gDevId, RSAPubKey, 1, srcData);
                CHECK(errCode == 0);
            }
            SUBCASE("extPriKeyDecrypt") {
                std::string enRSAData =
                    "YcO5rixW4+4UPy7YtlKks6RtU3mnc1V6oqyj4KS38zG7NHfaff4YE1FU2Jzabh0KM545FXK6KDr9A4Hgbl9JlgWSCIcdhChDu+5V1Wi+X+"
                    "754GdWcxgzgBVA2yeF6iEHidt9XGSOxaIm5W8sm0kuUOjqfY5xdgqDwe8um7RNadWM+6GkqacVR4K71bXKPNHEcYo8Z1C5S3eR+J0HbCfEvKWSEUZo5V1ZdEf/obS/"
                    "c5z1kIIZEkBxUkchSYIr6Egjfz6a1zfLX0vYIkSZelyWcvKJkGxdsjrio8ywcTUktZakebItiDuNOz+eeGJ6RVZEw5sJkQFYoYJS7G01D/"
                    "o2O5pU7Y2943T14xVvDFxZLnZccybwsfCLOibcoHfjIlc2ICt3Q4R3y3VYF45ip48171/Y/"
                    "1q8l+EDIU4Qv6KVOXxKOLoTJmDUbtTy1mRvveguvdE0VbjeUXjZkGak0o8faAcLBBUGYFFaaFbpv0kSfMH/"
                    "7LKQDeJebV227fhPL9Djx1CPGvkIZFJptahcsbeKIz7QJIrc7kA+GAximu+vPtGxkugqrZSKmWPG7qLZrYXyJ8fDfxc73+nE9YBiq59JIFPgj2ub4HdlA+"
                    "f7UX4nwJs0PRTF+KTk2R6F0vjzRRQroTKUqy4K8Gt6fNihVrAhKiYiFwqWAczorI5Nznc5uaw=";

                std::string RSAPriKey =
                    "AAABAAAIAADgY8SKsv2KsM85Wn7qzJGU0VO1EWUvQxob63wJilqit+"
                    "3Iq0tKeuTrg803N48XJG9V17ux9e3FrFfhxpU4F1o7mNJm6QllKxiAT2R7L3T5NggZgdQRoY3e/yah0tjInDmIwI9w4Ie/m+czHZ14KaVJECdpv0G2uNlz0rVrC/"
                    "jYZBxJjDOB+FHJdoQME1QIHgNkLAEFnAm/vqvNGqCcJHpv5QbrGdWpMNEbw9OwprTBW2Odx0Ya2OZU/YDj9Ts2nq6A50VLQVaJmptc4C/"
                    "mc31qI98Ft5fH3Z9pMU+te39CY5ioxJBgIMnHMIBMAdCazFDFbf/K74wZY/"
                    "xITDLfSeEbAAEAAdPa3lHltKQhU0VfP70H79uF13Z5OgNpY1lA+Bc53WEMLyDrOWbUqRYDltmvRxYefE+cI8Zd32Rm14J/"
                    "L5uJ0NO78crf8tLl4XgjfUchA1vXu8X5YfRTh9MnK55Vp/"
                    "+gIGIRDF0SYJhz5dHJAEfXagqVieC5hp2x4P0Nz+OFpikGdt8S+qCpsmJBint3CoG6yewcXpHMtKs0Z556ytkoPo7XwDSxwHAhHC1NOFWoE5wXW+4H6ZRM6di/"
                    "vKRLlA7/DI2SnlpfdR7Cp+TAKz8f0n9g4hQh+/"
                    "reyiYG7Wxx3afOWcTijEBvL6J+eev7xCldoV8LvY5vzAgfnPKCq7+kdaHkMk51h5AwUenT89vN5iZ5h65ZzkBGP4OIPc+CRd+kYcHNpgBMKkJt5eXJx+"
                    "h50lh5ycmWAnO7x6hCEfHyUbpAZ+wiDfe6Pc4gHZfsAjmeP/wjye32T5AoQixCKh5ofAhfhgxt1jg7gaY8Tc+htAXCDYZN0GYL77/IdAvjLjQcp/"
                    "u6uSz96fJid1YYgitbYy0XQ6MWJaurnl94+Z/mrmNn7JUEWeA0vA9g7DK1KE42eadtRUPQFM+S+D/"
                    "hHxHGR6k98FkNqv0AJzW58eURPmqDMU6u1mw+fBSU86ltDubXYq4sx6dEiVCNeIomyMfUBJbXXExwrSuponXaC4bGSKJtb+"
                    "x8rwieoBSNK3ggBw0rMdBqo4GV5N6iFOC8Npku5+FKvSL+Yl0X1+86lY2HaLjSzGk8hmcNfLygukcy9DdBsvVFzC4toxtNTlM1gHxW5ldbw/H21SiQ/"
                    "C34pzshTv4G3VNSJU08p3MfpdREUnuJl3Qqu1/LrW8aEadZbUPsK2FnxeUaqB4ufuslQfs/dnAfIY0PiUzERX6Hay60/"
                    "Xr8ndYI5H9Mj1DLojAdA1N3Uru6CrDYMMqze6n9Y4fUYwIdwdv4Sksn+00ut9YBkadCb4z/"
                    "G9JnUycBsRn1yXCwNzCGDw3LZdSZsYh0o2J4Bm5R5Lw3AuhQrKC9psLDr7A3zT2rdwphIQEWWAeE5mf7QW4+eWDjwr+"
                    "l8a0CjIoRYnWMyjL8jAzuZhrzZobsMqQFhKmNGwIggnO7vykMC2dpyK6OsOwafPOw8JN2/"
                    "71Ma0T7w9bhhFiUILliDyhVh5cxSkKswWVEGm60SUtDulfSBbaHllS4GY4U92VFk5LDnadh";

                errCode = gDevice.syncExtPriKeyDecrypt(gDevId, RSAPriKey, 1, enRSAData);
                CHECK(errCode == 0);
            }
        }
    }
};

TEST_CASE("import pfx for test 'make P10'") {
    std::string strAppName;
    if (isKoalSoft) {
        strAppName = "app";
        SUBCASE("createApp") {
            errCode = gDevice.syncCreateApp(gDevId, strAppName, "admin", 10, "1qaz!QAZ", 10, 255);
            CHECK(errCode == 0);
        }
        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, strAppName, 1, "1qaz!QAZ");
            CHECK(errCode == 0);
        }
    } else {
        strAppName = APPNAME;
        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, strAppName, 1, PINCODE);
            CHECK(errCode == 0);
        }
    }

    SUBCASE("createContainer") {
        errCode = gDevice.syncCreateContainer(gDevId, strAppName, "rsa");
        CHECK(errCode == 0);
    }

    SUBCASE("createContainer") {
        errCode = gDevice.syncCreateContainer(gDevId, strAppName, "ecc");
        CHECK(errCode == 0);
    }

    SUBCASE("ECC") {
        SUBCASE("genKeypair") {
            errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "ecc", "0", "2048", 1);
            CHECK(errCode == 0);
        }
        SUBCASE("importPfxCert") {
            std::string eccPfxStr =
                "MIIEEgIBAzCCA9gGCSqGSIb3DQEHAaCCA8kEggPFMIIDwTCCArcGCSqGSIb3DQEHBqCCAqgwggKkAgEAMIICnQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIKOPAUKhj"
                "zAgCAggAgIICcFQRSy/C6EvDqMdWJyGKmxvzCIJEqPdQnzc8NqRvxqJc0/2EZymOOpVihv1sl2YzIcPlrjD8m86+72hvGToW+Fv35ZDvoDmml5HKFb/"
                "AhMwe27QwGo73p7v6yTjkLzSz4JdUv6gRTUnJUiVbcJuGN2qS4ByyjEw10vCu6lRKNgOwrv6Krt0U6W+0Bxc78VUpYkxc95jOA/"
                "F6uOaSGZAosLL+biXc8GOZ0meZz92NUqWDkzySFHzi7qL6UcFJ6meT09Nc3K6SeJfKACWlXclNka7wg5pMWrw21cGsRGo3xyTeQME4tFTWWieHFnGvjo/"
                "tuouRPdiuYWMNJj5JcGGPL7uH8w/mnJPaero6DOCrMf8o4p/IrShkH3njNHzeolEu652yHRZnP/hGTSee5/XETRX0CTHrljyx2Lcp7w+dyRC5dIGTwvUk/mTFA5C/"
                "Yf775OwO+"
                "45gEjp6wEMZzs2kNUHj9cLt7D7EEZYJeaZLeSgObsrMgvGaR1xWMkVIcr9l9rpzV2Qtb4svE5uQy3WHrR4MbnQjK9MMTzR2CSnLjKlderqNWT2HFUnp1hXix6bBK7TjWQu+"
                "J+p1KFtr/ss5xahJbyeWIasSqj5Aoduugzq+wMLLBhC/DzXWRgwcVJJ6vtAPSx3nPH2aliKv1VheoIsfza+0jUWR/"
                "kydCCHNyIQ75tXdiqlMqcREj4u13Gjsq83Sn5Mf5iJcp1tMXKFebDgqtHa8BPN+oz0yfTMWZ6eGmr5s0H6fl6fbr+KrFG3UMAoQL7/"
                "0jLXPpXZpmgWROX5QL5iJuOkmAVdQAx+"
                "BDsjhu3v8wqqGZfXG8Xxi7dukZTCCAQIGCSqGSIb3DQEHAaCB9ASB8TCB7jCB6wYLKoZIhvcNAQwKAQKggbQwgbEwHAYKKoZIhvcNAQwBAzAOBAhMkZAWBq4B/"
                "QICCAAEgZAPkzeWCH8ZIRMn1yIGaVMLeGMafQ+ADypKJS/IuPQP58tnvh3udIDJ04ZuH6QsXXzr4xOfBaTHRe/M9ZvKoRkkx7a9PgCaxdo9IN40/"
                "1bqBeOvVNLrHghPVx5rTYhWDUA/+PkEDAnYXvTuObbJr8Lph0le6AHMlDZXtHl7m/"
                "XDPZ2NqVjVEN0dQwotcelCaJExJTAjBgkqhkiG9w0BCRUxFgQU8xfDWkJHSbCMuP6dl1INzjODK/8wMTAhMAkGBSsOAwIaBQAEFNsS6yoBrJXQAIEU/"
                "60AjQn7eAtfBAiFJYY31zOvHwICCAA=";

            // if(isKoalSoft)
            // errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, "app", "ecc", 0, "123456", eccPfxStr);
            // else
            errCode = gEnRoll.syncImportPfxCert(gDevId, strAppName, "ecc", eccPfxStr, "123456");
            CHECK(errCode == 0);
        }

        SUBCASE("makePkcs10") {
            errCode =
                gEnRoll.syncMakePkcs10(gDevId, strAppName, "ecc", "C=CN,ST=shanxi,O=koal,OU=koal,CN=shidawei,emailAddress=shidawei@koal.com", 1, 0);
            CHECK(errCode == 0);
        }
    }

    SUBCASE("RSA") {
        SUBCASE("genKeypair") {
            errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "rsa", "1", "2048", 1);
            CHECK(errCode == 0);
        }

        SUBCASE("importPfxCert") {
            std::string rsaPfxStr =
                "MIIJYQIBAzCCCScGCSqGSIb3DQEHAaCCCRgEggkUMIIJEDCCA8cGCSqGSIb3DQEHBqCCA7gwggO0AgEAMIIDrQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIO1cTp9Qm"
                "g1cCAggAgIIDgEdIvau0WxxSU1G7DiwupoZX2OmOGuG8voZWMh4yGcs0mUGzgymaBqySpWDqKySbTro7JWa8PKjoHfm6mKUwKnPLruoZc9SYTIRfzpSC4AEU8jMRmLOKSNIf"
                "qqn0bhNwH8yFLkwPlQRhqvmDw3LmCzANP829v6iAHzlDnzfYrG+XdtDs399N99eJzbEyulwI8zYXCpFSdg3EFIO0nFy0k55fccg3+"
                "MvEdoUADmKukwwzCk1tpARU0xokIFpyTrcU1GF2gCLLCo9oSlaolLbh7JXLE+55Vd/URaGHQxjCqVMc5RkV7tOWXtPpjPBDCRdI2MNwd/"
                "eYMeHoxPF3ih2rNOLV4me8WxhLIJpqix1F0PEmIlMqPxuyA7svZxeAoUQcbIV/G4+7bt/14YvIAT39GjpyiiQU2cG/l49PgNYJMIFWKY/agW+nVN8QDpnX9m6yh/"
                "LzMcGgGopfA8rFn3n6ZOfjytScVxn/GvrSekQYqRm7hvqJo5SyT1ceGv0DvBMiuDyoewzRcWMV/YwnaW1khOHAgymlQ+3WmEEpG2m+VYgmnelL8lNPd1k0b+kh6LzUjWVj/"
                "jxMQvALkYYYBnV4YEIeSzcRIMKPovfVIMLFN+RAOHVUEfpyJg9dogOPgR0nIpLmbm8svSK8KIVT2yNFxrI7nubvBW0ybWqP0THZjRiv/"
                "Aimgb628EmJSL19LBksylvkpvRmY38rUdJ3zVxAijz9kwq5LfptYBiNoMKM+i0pnwLAoz3Aa+gXdqFrC6AlRoXmnWrtG1G8Ls8N4W6M4l8quz+0bGdxBQ+"
                "XPaZjAG2FwS2Sw8xDvVPgfc6ss+tvvuzVs23h9RnYXTgLqSm1ngOJrKti1pRr+ayBh8aM993FZqaUDVy1hM7RODPpHBQF9KBHEszuLnp4MqTwRtn7GivQ/"
                "Fsawk9pAhQLssyL1LvX+Em96jRshAqkb4KXUa6AJpLjNIFhCgsu5XamOC6u5JjHJK8D5PdtaMapU8PDt4w071D5QDFe8XPQcWxvJ1EgyxyrrgeVXqXiW9fy4TY5AjSK4qe/"
                "CW5Ae/NsAkzNzgRBm/"
                "mWHmIEFcDVXBwq8iayc4zi+ebXFDqMDlRyhm3RDkxcM5daIQhz2Ju7rIpbJX8Baay5DK1DCm3D+"
                "PY5NnAfpswQdcf28uyuOyjyEfMcgOabdRMwdNpaVKu4SyrxKPulMIIFQQYJKoZIhvcNAQcBoIIFMgSCBS4wggUqMIIFJgYLKoZIhvcNAQwKAQKgggTuMIIE6jAcBgoqhkiG9"
                "w0BDAEDMA4ECHTjLAeT99mFAgIIAASCBMjflyOyXYKyt8qos/"
                "TMhZ4PX7J2FnfToq0D5cniGz246LLEKytHPzyC7afdYx831MirZvZ05Z1V0sWsnBvXuoqPb2RQKLx5aZfDfEZ1moh1LV8H906aBkxaLA5ioA9xQAd1E33R/"
                "S7k5uLhTeqwDRPGWV9/hHt4tbPZdDkk/"
                "oFzKTuQL3sNfwjEzqqWdh4cJDJcfLbgcB8sIB8zIaYrACAR4eBOAn0cBFJgPIwdh5IaHYa6cXUK4U9b5zeYKkQpBf2fdPynRggv14ya2nEIXdR4eXGm33SUZIbTb0AyjlHaH"
                "Pb8HvD7jG9nBvrA/"
                "HfsYM4TsPq3vwNlpafk+NnDkGaFlHDBI0AlkDdGwhhOEtVx5MZ5TUXPtyFR0B6jwM4yTuBPWW10purPviUi0+ymBHfAJSEzW3HESLZnn2e6OKjMSJ5gOVgO/"
                "Oo7WeTs7f6P8MytJTdzC8IUqRfobmu+VnBykObPKtPq/"
                "eLt7QZeGLI6nbMQoswYBTVhQx3cwut4M8ZgblEQ2fSk5T3M9F52YXEjLwW9zkiZKjN7Fkr+DgL8CQWEVpUh0NoRqrFYoKX/fyQyn/"
                "CQdQNG1Cuv5WSN5HLIT287bx80Qa65Pm7oOxY2tfZmRg+ZYE9LJAEt4/"
                "Ru26ilfHR9fx19OQyijgndXL+Nn1PW4PkpzRKOLuxZPbByDCxcC7wgsaoLx7xjPDFU5WOq0fvJ2k6UaoVhwnV+VrEA/3C9Nj04XlDgI9YUkb17f1pUA/"
                "qBOTwTgiU8ajwcZdV/"
                "E9Wu+9m3Q531MEJ3ZHUGZ7GEWp7NdXDGbvRUJXoT0gQNdVESc8RGFia8rgFN5bloXQLmk6Yb1oLCTxP9pf89JsiCap8GmqWMngoZzfCtd8nNjfEupopJJXQuZ+"
                "d0j0ePXUivK8kXV8unsl3t3VVtM2m+D5C0fNIDsb//swUUHrieILVyGOESCAqgCok1o0p+FUYuhrMqL5oSKAN9cis0uHW5ZgkC9FxKLczTJN/"
                "aknhT6Re0zTqoAvUpUxWQMxytKNCmtuBuXem2mvPQk+X6gXHnuStv7181KPAyHUJwNVBrpY3JUVktOSkt+TNvi9hBFP5gdo1nZNkcA2B/hC/"
                "x5UH9sHl3Q9E7bPyQf2TUBf1Uogihub/V326wjMRz2dSc1ojD/rqHVk1yLyJGCz/"
                "iFZZIjp12w7hGyPvOqrVVIJrlPDcqEohUDBwp56Da1AfmWDzWuhKuTjMOY77ge15JR6CLiDK7xbNSppOhdcaxgKPMOmmtinbH94hPrtBYXhgRcU/"
                "ZCZQ34Q48Kjmo4LRJPOifCaX1HsL2D8z85qwB9Fe5iQnubsE8UBpi7ucYEZt1sd5jLLeVPbiavaYcCtWn7D4dToCp2c6hhG9s2L0pY+YRVa4imexUIIdKmfD2hB39NuADbA/"
                "aKX9rrSI34MfGGwZcKwJ76Wp3n9mo9xHqAnm6QwXpNIxvqhHVD7ccCi2Kme9JMZFzaW8Ue5xJQTeoJrG5iH/shd/"
                "vFNbZsu9n4sZ3KuhoOEd+oMB7AYZWa7BDYkiQTd62z0CVhj8pAw18k+/i314LkCo4O6hZCnX+9y/QsZoBJ+ohEU9e+gI+c/r83iC6GhJKRj2ycx/"
                "8cCwPPGS33LHCn3cxJTAjBgkqhkiG9w0BCRUxFgQUbRjsUyyfanyD3fcz/"
                "etuBwHXcOUwMTAhMAkGBSsOAwIaBQAEFKw31nAlRW7tugM9friGjnYkRmviBAjwMBhsIOs9yAICCAA=";

            // if(isKoalSoft)
            // errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, "app", "rsa", 0, "123456", rsaPfxStr);
            // else
            errCode = gEnRoll.syncImportPfxCert(gDevId, strAppName, "rsa", rsaPfxStr, "123456");

            CHECK(errCode == 0);
        }

        SUBCASE("makePkcs10") {
            errCode =
                gEnRoll.syncMakePkcs10(gDevId, strAppName, "rsa", "C=CN,ST=shanxi,O=koal,OU=koal,CN=shidawei,emailAddress=shidawei@koal.com", 1, 0);
            CHECK(errCode == 0);
        }

        SUBCASE("genKeypair") {
            errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "rsa", "1", "1024", 1);
            CHECK(errCode == 0);
        }

        SUBCASE("importPfxCert") {
            std::string rsaPfxStr =
                "MIIJYQIBAzCCCScGCSqGSIb3DQEHAaCCCRgEggkUMIIJEDCCA8cGCSqGSIb3DQEHBqCCA7gwggO0AgEAMIIDrQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIO1cTp9Qm"
                "g1cCAggAgIIDgEdIvau0WxxSU1G7DiwupoZX2OmOGuG8voZWMh4yGcs0mUGzgymaBqySpWDqKySbTro7JWa8PKjoHfm6mKUwKnPLruoZc9SYTIRfzpSC4AEU8jMRmLOKSNIf"
                "qqn0bhNwH8yFLkwPlQRhqvmDw3LmCzANP829v6iAHzlDnzfYrG+XdtDs399N99eJzbEyulwI8zYXCpFSdg3EFIO0nFy0k55fccg3+"
                "MvEdoUADmKukwwzCk1tpARU0xokIFpyTrcU1GF2gCLLCo9oSlaolLbh7JXLE+55Vd/URaGHQxjCqVMc5RkV7tOWXtPpjPBDCRdI2MNwd/"
                "eYMeHoxPF3ih2rNOLV4me8WxhLIJpqix1F0PEmIlMqPxuyA7svZxeAoUQcbIV/G4+7bt/14YvIAT39GjpyiiQU2cG/l49PgNYJMIFWKY/agW+nVN8QDpnX9m6yh/"
                "LzMcGgGopfA8rFn3n6ZOfjytScVxn/GvrSekQYqRm7hvqJo5SyT1ceGv0DvBMiuDyoewzRcWMV/YwnaW1khOHAgymlQ+3WmEEpG2m+VYgmnelL8lNPd1k0b+kh6LzUjWVj/"
                "jxMQvALkYYYBnV4YEIeSzcRIMKPovfVIMLFN+RAOHVUEfpyJg9dogOPgR0nIpLmbm8svSK8KIVT2yNFxrI7nubvBW0ybWqP0THZjRiv/"
                "Aimgb628EmJSL19LBksylvkpvRmY38rUdJ3zVxAijz9kwq5LfptYBiNoMKM+i0pnwLAoz3Aa+gXdqFrC6AlRoXmnWrtG1G8Ls8N4W6M4l8quz+0bGdxBQ+"
                "XPaZjAG2FwS2Sw8xDvVPgfc6ss+tvvuzVs23h9RnYXTgLqSm1ngOJrKti1pRr+ayBh8aM993FZqaUDVy1hM7RODPpHBQF9KBHEszuLnp4MqTwRtn7GivQ/"
                "Fsawk9pAhQLssyL1LvX+Em96jRshAqkb4KXUa6AJpLjNIFhCgsu5XamOC6u5JjHJK8D5PdtaMapU8PDt4w071D5QDFe8XPQcWxvJ1EgyxyrrgeVXqXiW9fy4TY5AjSK4qe/"
                "CW5Ae/NsAkzNzgRBm/"
                "mWHmIEFcDVXBwq8iayc4zi+ebXFDqMDlRyhm3RDkxcM5daIQhz2Ju7rIpbJX8Baay5DK1DCm3D+"
                "PY5NnAfpswQdcf28uyuOyjyEfMcgOabdRMwdNpaVKu4SyrxKPulMIIFQQYJKoZIhvcNAQcBoIIFMgSCBS4wggUqMIIFJgYLKoZIhvcNAQwKAQKgggTuMIIE6jAcBgoqhkiG9"
                "w0BDAEDMA4ECHTjLAeT99mFAgIIAASCBMjflyOyXYKyt8qos/"
                "TMhZ4PX7J2FnfToq0D5cniGz246LLEKytHPzyC7afdYx831MirZvZ05Z1V0sWsnBvXuoqPb2RQKLx5aZfDfEZ1moh1LV8H906aBkxaLA5ioA9xQAd1E33R/"
                "S7k5uLhTeqwDRPGWV9/hHt4tbPZdDkk/"
                "oFzKTuQL3sNfwjEzqqWdh4cJDJcfLbgcB8sIB8zIaYrACAR4eBOAn0cBFJgPIwdh5IaHYa6cXUK4U9b5zeYKkQpBf2fdPynRggv14ya2nEIXdR4eXGm33SUZIbTb0AyjlHaH"
                "Pb8HvD7jG9nBvrA/"
                "HfsYM4TsPq3vwNlpafk+NnDkGaFlHDBI0AlkDdGwhhOEtVx5MZ5TUXPtyFR0B6jwM4yTuBPWW10purPviUi0+ymBHfAJSEzW3HESLZnn2e6OKjMSJ5gOVgO/"
                "Oo7WeTs7f6P8MytJTdzC8IUqRfobmu+VnBykObPKtPq/"
                "eLt7QZeGLI6nbMQoswYBTVhQx3cwut4M8ZgblEQ2fSk5T3M9F52YXEjLwW9zkiZKjN7Fkr+DgL8CQWEVpUh0NoRqrFYoKX/fyQyn/"
                "CQdQNG1Cuv5WSN5HLIT287bx80Qa65Pm7oOxY2tfZmRg+ZYE9LJAEt4/"
                "Ru26ilfHR9fx19OQyijgndXL+Nn1PW4PkpzRKOLuxZPbByDCxcC7wgsaoLx7xjPDFU5WOq0fvJ2k6UaoVhwnV+VrEA/3C9Nj04XlDgI9YUkb17f1pUA/"
                "qBOTwTgiU8ajwcZdV/"
                "E9Wu+9m3Q531MEJ3ZHUGZ7GEWp7NdXDGbvRUJXoT0gQNdVESc8RGFia8rgFN5bloXQLmk6Yb1oLCTxP9pf89JsiCap8GmqWMngoZzfCtd8nNjfEupopJJXQuZ+"
                "d0j0ePXUivK8kXV8unsl3t3VVtM2m+D5C0fNIDsb//swUUHrieILVyGOESCAqgCok1o0p+FUYuhrMqL5oSKAN9cis0uHW5ZgkC9FxKLczTJN/"
                "aknhT6Re0zTqoAvUpUxWQMxytKNCmtuBuXem2mvPQk+X6gXHnuStv7181KPAyHUJwNVBrpY3JUVktOSkt+TNvi9hBFP5gdo1nZNkcA2B/hC/"
                "x5UH9sHl3Q9E7bPyQf2TUBf1Uogihub/V326wjMRz2dSc1ojD/rqHVk1yLyJGCz/"
                "iFZZIjp12w7hGyPvOqrVVIJrlPDcqEohUDBwp56Da1AfmWDzWuhKuTjMOY77ge15JR6CLiDK7xbNSppOhdcaxgKPMOmmtinbH94hPrtBYXhgRcU/"
                "ZCZQ34Q48Kjmo4LRJPOifCaX1HsL2D8z85qwB9Fe5iQnubsE8UBpi7ucYEZt1sd5jLLeVPbiavaYcCtWn7D4dToCp2c6hhG9s2L0pY+YRVa4imexUIIdKmfD2hB39NuADbA/"
                "aKX9rrSI34MfGGwZcKwJ76Wp3n9mo9xHqAnm6QwXpNIxvqhHVD7ccCi2Kme9JMZFzaW8Ue5xJQTeoJrG5iH/shd/"
                "vFNbZsu9n4sZ3KuhoOEd+oMB7AYZWa7BDYkiQTd62z0CVhj8pAw18k+/i314LkCo4O6hZCnX+9y/QsZoBJ+ohEU9e+gI+c/r83iC6GhJKRj2ycx/"
                "8cCwPPGS33LHCn3cxJTAjBgkqhkiG9w0BCRUxFgQUbRjsUyyfanyD3fcz/"
                "etuBwHXcOUwMTAhMAkGBSsOAwIaBQAEFKw31nAlRW7tugM9friGjnYkRmviBAjwMBhsIOs9yAICCAA=";

            // if(isKoalSoft)
            // errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, "app", "rsa", 0, "123456", rsaPfxStr);
            // else
            errCode = gEnRoll.syncImportPfxCert(gDevId, strAppName, "rsa", rsaPfxStr, "123456");

            CHECK(errCode == 0);
        }

        SUBCASE("makePkcs10") {
            errCode =
                gEnRoll.syncMakePkcs10(gDevId, strAppName, "rsa", "C=CN,ST=shanxi,O=koal,OU=koal,CN=shidawei,emailAddress=shidawei@koal.com", 1, 0);
            CHECK(errCode == 0);
        }
    }

    SUBCASE("exportPublicKey") {
        errCode = gDevice.syncExportPublicKey(gDevId, strAppName, "rsa", 1);
        CHECK(errCode == 0);

        errCode = gDevice.syncExportPublicKey(gDevId, strAppName, "rsa", 0);
        CHECK(errCode == 0);
    }

    SUBCASE("delContainer") {
        errCode = gDevice.syncDelContainer(gDevId, strAppName, "rsa");
        CHECK(errCode == 0);
    }

    SUBCASE("delContainer") {
        errCode = gDevice.syncDelContainer(gDevId, strAppName, "ecc");
        CHECK(errCode == 0);
    }

    if (isKoalSoft) {
        SUBCASE("delApp") {
            errCode = gDevice.syncDelApp(gDevId, strAppName);
            CHECK(errCode == 0);
        }
    }
}

TEST_CASE("importEncKeypair" * doctest::skip()) {
    errCode = gEnRoll.syncImportEncKeypair(gDevId, "app", "rsa", "");
    CHECK(errCode == 0);
}

TEST_CASE("signData/verifyData") {
    std::string strAppName = "";

    if (isKoalSoft) {
        strAppName = "app";
        SUBCASE("createApp") {
            errCode = gDevice.syncCreateApp(gDevId, strAppName, "admin", 10, "1qaz!QAZ", 10, 255);
            CHECK(errCode == 0);
        }

        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, strAppName, 1, "1qaz!QAZ");
            CHECK(errCode == 0);
        }
    } else {
        strAppName = APPNAME;
        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, strAppName, 1, PINCODE);
            CHECK(errCode == 0);
        }
    }

    SUBCASE("createContainer") {
        errCode = gDevice.syncCreateContainer(gDevId, strAppName, "test");
        CHECK(errCode == 0);
    }

    SUBCASE("createContainer") {
        errCode = gDevice.syncCreateContainer(gDevId, strAppName, "rsa");
        CHECK(errCode == 0);
    }

    SUBCASE("genKeypair") {
        errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "test", "0", "2048", 1);
        CHECK(errCode == 0);
    }

    /// signData签名类型传1时，仅适用于pm测试key，在此不做测试

    SUBCASE("SM2") {
        SUBCASE("signData") {
            errCode = gSignx.syncSignData(gDevId, strAppName, "test", "MTIzNDU2NzgxMjM0NTY3OA==", 1, "2");
            CHECK(errCode == 0);
        };
        SUBCASE("verifySignData") {
            errCode = gSignx.syncVerifySignData(gDevId, strAppName, "test", "MTIzNDU2NzgxMjM0NTY3OA==", gSignx.syncGetSignData(), 1, 2);
            CHECK(errCode == 0);
        };
    }

    SUBCASE("RSA") {
        SUBCASE("genKeypair") {
            errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "rsa", "1", "2048", 1);
            CHECK(errCode == 0);
        }

        SUBCASE("signData") {
            errCode = gSignx.syncSignData(gDevId, strAppName, "rsa", "MTIzNDU2NzgxMjM0NTY3OA==", 1, "2");
            CHECK(errCode == 0);
        };

        SUBCASE("verifySignData") {
            errCode = gSignx.syncVerifySignData(gDevId, strAppName, "rsa", "MTIzNDU2NzgxMjM0NTY3OA==", gSignx.syncGetSignData(), 1, 2);
            CHECK(errCode == 0);
        };
    }

    SUBCASE("delContainer") {
        errCode = gDevice.syncDelContainer(gDevId, strAppName, "test");
        CHECK(errCode == 0);
    }

    SUBCASE("delContainer") {
        errCode = gDevice.syncDelContainer(gDevId, strAppName, "rsa");
        CHECK(errCode == 0);
    }

    if (isKoalSoft) {
        SUBCASE("delApp") {
            errCode = gDevice.syncDelApp(gDevId, strAppName);
            CHECK(errCode == 0);
        }
    }
}

TEST_CASE("signMessage/verifyMessage") {
    if (isKoalSoft) {
        SUBCASE("createApp") {
            errCode = gDevice.syncCreateApp(gDevId, "app", "admin", 10, "1qaz!QAZ", 10, 255);
            CHECK(errCode == 0);
        }

        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, "app", 1, "1qaz!QAZ");
            CHECK(errCode == 0);
        }

        SUBCASE("createContainer") {
            errCode = gDevice.syncCreateContainer(gDevId, "app", "cont");
            CHECK(errCode == 0);
        }

        SUBCASE("importPfx2SkfFile") {
            std::string eccPfxStr =
                "MIIEEgIBAzCCA9gGCSqGSIb3DQEHAaCCA8kEggPFMIIDwTCCArcGCSqGSIb3DQEHBqCCAqgwggKkAgEAMIICnQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIKOPAUKhj"
                "zAgCAggAgIICcFQRSy/C6EvDqMdWJyGKmxvzCIJEqPdQnzc8NqRvxqJc0/2EZymOOpVihv1sl2YzIcPlrjD8m86+72hvGToW+Fv35ZDvoDmml5HKFb/"
                "AhMwe27QwGo73p7v6yTjkLzSz4JdUv6gRTUnJUiVbcJuGN2qS4ByyjEw10vCu6lRKNgOwrv6Krt0U6W+0Bxc78VUpYkxc95jOA/"
                "F6uOaSGZAosLL+biXc8GOZ0meZz92NUqWDkzySFHzi7qL6UcFJ6meT09Nc3K6SeJfKACWlXclNka7wg5pMWrw21cGsRGo3xyTeQME4tFTWWieHFnGvjo/"
                "tuouRPdiuYWMNJj5JcGGPL7uH8w/mnJPaero6DOCrMf8o4p/IrShkH3njNHzeolEu652yHRZnP/hGTSee5/XETRX0CTHrljyx2Lcp7w+dyRC5dIGTwvUk/mTFA5C/"
                "Yf775OwO+"
                "45gEjp6wEMZzs2kNUHj9cLt7D7EEZYJeaZLeSgObsrMgvGaR1xWMkVIcr9l9rpzV2Qtb4svE5uQy3WHrR4MbnQjK9MMTzR2CSnLjKlderqNWT2HFUnp1hXix6bBK7TjWQu+"
                "J+p1KFtr/ss5xahJbyeWIasSqj5Aoduugzq+wMLLBhC/DzXWRgwcVJJ6vtAPSx3nPH2aliKv1VheoIsfza+0jUWR/"
                "kydCCHNyIQ75tXdiqlMqcREj4u13Gjsq83Sn5Mf5iJcp1tMXKFebDgqtHa8BPN+oz0yfTMWZ6eGmr5s0H6fl6fbr+KrFG3UMAoQL7/"
                "0jLXPpXZpmgWROX5QL5iJuOkmAVdQAx+"
                "BDsjhu3v8wqqGZfXG8Xxi7dukZTCCAQIGCSqGSIb3DQEHAaCB9ASB8TCB7jCB6wYLKoZIhvcNAQwKAQKggbQwgbEwHAYKKoZIhvcNAQwBAzAOBAhMkZAWBq4B/"
                "QICCAAEgZAPkzeWCH8ZIRMn1yIGaVMLeGMafQ+ADypKJS/IuPQP58tnvh3udIDJ04ZuH6QsXXzr4xOfBaTHRe/M9ZvKoRkkx7a9PgCaxdo9IN40/"
                "1bqBeOvVNLrHghPVx5rTYhWDUA/+PkEDAnYXvTuObbJr8Lph0le6AHMlDZXtHl7m/"
                "XDPZ2NqVjVEN0dQwotcelCaJExJTAjBgkqhkiG9w0BCRUxFgQU8xfDWkJHSbCMuP6dl1INzjODK/8wMTAhMAkGBSsOAwIaBQAEFNsS6yoBrJXQAIEU/"
                "60AjQn7eAtfBAiFJYY31zOvHwICCAA=";
            errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, "app", "cont", 1, "123456", eccPfxStr);
            CHECK(errCode == 0);
        }

        SUBCASE("ECC") {
            SUBCASE("signMessage") {
                errCode = gSignx.syncSignMessage(gDevId, "app", "cont", "MTIzNDU2NzgxMjM0NTY3OA==", 3, "0", 1, 0);
                CHECK(errCode == 0);
            }

            SUBCASE("verifyMessage") {
                errCode = gSignx.syncVerifyMessage("MTIzNDU2NzgxMjM0NTY3OA==", gSignx.syncGetP7SignData());
                CHECK(errCode == 0);
            }
            SUBCASE("verifySignedMessage") {
                std::string cert =
                    "MIICETCCAbigAwIBAgIGIBkFCAAFMAoGCCqBHM9VAYN1MFsxCzAJBgNVBAYTAkNOMQ8wDQYDVQQIDAZzaGFueGkxDTALBgNVBAcMBHhpYW4xDTALBgNVBAoMBGtvYWwx"
                    "CzAJBgNVBAsMAmNhMRAwDgYDVQQDDAdlY2NSb290MB4XDTE5MTAyMDEyMjIxNFoXDTI5MTAxNzEyMjIxNFowOzELMAkGA1UEBhMCQ04xDzANBgNVBAgMBnNoYW54aTEN"
                    "MAsGA1UECgwEa29hbDEMMAoGA1UEAwwDempqMFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEFlQQtdVN0r0EJe9CP1KWHCUWbNwKW0itzGqR9zPB3wkjotxG0ITco9V0"
                    "zWpsqOLgfDqsUvQw+"
                    "YgPyH7fvdIQEqOBhzCBhDAJBgNVHRMEAjAAMAsGA1UdDwQEAwIFIDAqBglghkgBhvhCAQ0EHRYbR21TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBSuR"
                    "uprzbPKZmtP/"
                    "k71BvKn9yxyUTAfBgNVHSMEGDAWgBStNt6lbdm0B8T9oeLvpo84hqmvyTAKBggqgRzPVQGDdQNHADBEAiALsovOfj0FVLEkJ+"
                    "ZCfCAXeKrenU2NyP1xsYOGysd61wIgHhzR/iXD43KRCCGUva7lfIcjSE6/fVIUXHT+6++Yg3k=";

                errCode = gSignx.syncVerifySignedMessage("MTIzNDU2NzgxMjM0NTY3OA==", gSignx.syncGetP7SignData(), cert);
                CHECK(errCode == 0);
            }
        }

        SUBCASE("delContainer") {
            errCode = gDevice.syncDelContainer(gDevId, "app", "cont");
            CHECK(errCode == 0);
        }

        SUBCASE("delApp") {
            errCode = gDevice.syncDelApp(gDevId, "app");
            CHECK(errCode == 0);
        }

    } else {
        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, APPNAME, 1, PINCODE);
            CHECK(errCode == 0);
        }

        SUBCASE("ECC") {
            SUBCASE("signMessage") {
                errCode = gSignx.syncSignMessage(gDevId, APPNAME, CONNAME, "MTIzNDU2NzgxMjM0NTY3OA==", 3, "0", 1, 0);
                CHECK(errCode == 0);
            }

            SUBCASE("verifyMessage") {
                errCode = gSignx.syncVerifyMessage("MTIzNDU2NzgxMjM0NTY3OA==", gSignx.syncGetP7SignData());
                CHECK(errCode == 0);
            }
            SUBCASE("verifySignedMessage") {
                std::string cert =
                    "MIIBxjCCAWmgAwIBAgIMICUAAAAAAAAAAAAcMAwGCCqBHM9VAYN1BQAwIzELMAkGA1UEBhMCQ04xFDASBgNVBAMMC2NhYmVuZGlfc20yMB4XDTIwMDgyNjA0MDAwMFoX"
                    "DTIzMDgyNzAzNTk1OVowHjELMAkGA1UEBhMCQ04xDzANBgNVBAMMBnNtMjAwbzBZMBMGByqGSM49AgEGCCqBHM9VAYItA0IABH/V3rNNyw2P8eNRdy2tDcwXxbiyttI"
                    "qMFnMLecQ3fWXKD+"
                    "1z6bNPhDeM1nZ5n3bEiCwihXWoDzDClFvC20D4WyjgYUwgYIwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMEMA4GA1UdDwEB/wQEAwIAwDARBglghkgBhvhCAQ"
                    "EEBAMCAIAwHwYDVR0jBBgwFoAUgsOZo+"
                    "na9W65pWC4GbM/RhKhyBcwHQYDVR0OBBYEFPJoMpGX5gHxscNfDxgHBlxH0BeXMAwGCCqBHM9VAYN1BQADSQAwRgIhAPCa82ctj9gSzZK4GL8CfXSLsL7ostcS+"
                    "WKePVKycjd9AiEA7x4Yi4B+3Bwr1Vbd4z3xami2PqzMJRpPzxwP3zNNC30=";
                errCode = gSignx.syncVerifySignedMessage("MTIzNDU2NzgxMjM0NTY3OA==", gSignx.syncGetP7SignData(), cert);
                CHECK(errCode == 0);
            }
        }
    }
};

TEST_CASE("extPub/extCert Verify") {
    if (isKoalSoft) {
        // skffile SKF_DigestInit 只支持SGD_SM3

    } else {
        std::string signData =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA6kAnrhyKUSlDrG+QDU4asJai/"
            "GJ7EwyVm737TlMen8wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA5wtlJYotksp4HI59I7Vj9hECTRxu44FIbx69SweJIlQ=";

        ///外来公钥验签仅适用于pm测试key在此不做测试

        SUBCASE("extECCVerifyEx") {
            std::string cert =
                "MIIB3jCCAYOgAwIBAgIMKdkAAAAAAAAAAAB4MAwGCCqBHM9VAYN1BQAwJDELMAkGA1UEBhMCQ04xFTATBgNVBAMMDGxvY2FsX2NhX3NtMjAeFw0xOTExMjAxNjAwMDBaFw0y"
                "MjExMjAxNTU5NTlaMCkxCzAJBgNVBAYTAkNOMRowGAYDVQQDDBF0ZXN0MDAwMDAwMDAwMDAwMTBZMBMGByqGSM49AgEGCCqBHM9VAYItA0IABCC/"
                "YGHwjkXxrvvIGwF2JOEULAdyb27yRiX7G/pmoWu+/"
                "roATjTcgTpZUR2V7iCgdTAKfHl+"
                "h9BNTpQKMMyNz1ejgZMwgZAwEQYJYIZIAYb4QgEBBAQDAgCAMAsGA1UdDwQEAwIAwDAgBgNVHSUBAf8EFjAUBggrBgEFBQcDAgYIKwYBBQUHAwQwDAYDVR0TBAUwAwEBADAf"
                "BgNVHSMEGDAWgBT6af3+dG26lCr1wqX0G1WwOOsMGTAdBgNVHQ4EFgQUsYFnbwLwqN8HumSRocr5/"
                "yY0QtQwDAYIKoEcz1UBg3UFAANHADBEAiBsLQfj+uQaR38vtWK0jPKENs5wr4Keg/wOXsYTws8NhwIgTBAmJW66eQ+G51L0OxSjoA4Rlr/aAV7zVRnSxA7pnPI=";
            errCode = gSignx.syncExtECCVerifyEx(gDevId, cert, "MTIzNDU2NzgxMjM0NTY3OA==", signData);
            CHECK(errCode == 0);
        }
    }
}

TEST_CASE("dupCertWithTemplate") {
    if (isKoalSoft) {
        //预先导入签名，加密证书
        SUBCASE("createApp") {
            errCode = gDevice.syncCreateApp(gDevId, "app", "admin", 10, "1qaz!QAZ", 10, 255);
            CHECK(errCode == 0);
        }

        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, "app", 1, "1qaz!QAZ");
            CHECK(errCode == 0);
        }

        SUBCASE("createContainer") {
            errCode = gDevice.syncCreateContainer(gDevId, "app", "cont");
            CHECK(errCode == 0);
        }

        SUBCASE("importPfx2SkfFile") {
            std::string eccPfxStr =
                "MIIEEgIBAzCCA9gGCSqGSIb3DQEHAaCCA8kEggPFMIIDwTCCArcGCSqGSIb3DQEHBqCCAqgwggKkAgEAMIICnQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIKOPAUKhj"
                "zAgCAggAgIICcFQRSy/C6EvDqMdWJyGKmxvzCIJEqPdQnzc8NqRvxqJc0/2EZymOOpVihv1sl2YzIcPlrjD8m86+72hvGToW+Fv35ZDvoDmml5HKFb/"
                "AhMwe27QwGo73p7v6yTjkLzSz4JdUv6gRTUnJUiVbcJuGN2qS4ByyjEw10vCu6lRKNgOwrv6Krt0U6W+0Bxc78VUpYkxc95jOA/"
                "F6uOaSGZAosLL+biXc8GOZ0meZz92NUqWDkzySFHzi7qL6UcFJ6meT09Nc3K6SeJfKACWlXclNka7wg5pMWrw21cGsRGo3xyTeQME4tFTWWieHFnGvjo/"
                "tuouRPdiuYWMNJj5JcGGPL7uH8w/mnJPaero6DOCrMf8o4p/IrShkH3njNHzeolEu652yHRZnP/hGTSee5/XETRX0CTHrljyx2Lcp7w+dyRC5dIGTwvUk/mTFA5C/"
                "Yf775OwO+"
                "45gEjp6wEMZzs2kNUHj9cLt7D7EEZYJeaZLeSgObsrMgvGaR1xWMkVIcr9l9rpzV2Qtb4svE5uQy3WHrR4MbnQjK9MMTzR2CSnLjKlderqNWT2HFUnp1hXix6bBK7TjWQu+"
                "J+p1KFtr/ss5xahJbyeWIasSqj5Aoduugzq+wMLLBhC/DzXWRgwcVJJ6vtAPSx3nPH2aliKv1VheoIsfza+0jUWR/"
                "kydCCHNyIQ75tXdiqlMqcREj4u13Gjsq83Sn5Mf5iJcp1tMXKFebDgqtHa8BPN+oz0yfTMWZ6eGmr5s0H6fl6fbr+KrFG3UMAoQL7/"
                "0jLXPpXZpmgWROX5QL5iJuOkmAVdQAx+"
                "BDsjhu3v8wqqGZfXG8Xxi7dukZTCCAQIGCSqGSIb3DQEHAaCB9ASB8TCB7jCB6wYLKoZIhvcNAQwKAQKggbQwgbEwHAYKKoZIhvcNAQwBAzAOBAhMkZAWBq4B/"
                "QICCAAEgZAPkzeWCH8ZIRMn1yIGaVMLeGMafQ+ADypKJS/IuPQP58tnvh3udIDJ04ZuH6QsXXzr4xOfBaTHRe/M9ZvKoRkkx7a9PgCaxdo9IN40/"
                "1bqBeOvVNLrHghPVx5rTYhWDUA/+PkEDAnYXvTuObbJr8Lph0le6AHMlDZXtHl7m/"
                "XDPZ2NqVjVEN0dQwotcelCaJExJTAjBgkqhkiG9w0BCRUxFgQU8xfDWkJHSbCMuP6dl1INzjODK/8wMTAhMAkGBSsOAwIaBQAEFNsS6yoBrJXQAIEU/"
                "60AjQn7eAtfBAiFJYY31zOvHwICCAA=";
            errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, "app", "cont", 1, "123456", eccPfxStr);
            CHECK(errCode == 0);

            errCode = gEnRoll.syncImportPfx2SkfFile(gDevId, "app", "cont", 0, "123456", eccPfxStr);
            CHECK(errCode == 0);
        }

        SUBCASE("DupCertWithTemplate") {
            errCode = gSignx.syncDupCertWithTemplate(gDevId, "app", "cont", "1");
            CHECK(errCode == 0);

            errCode = gSignx.syncDupCertWithTemplate(gDevId, "app", "cont", "0");
            CHECK(errCode == 0);
        }

        SUBCASE("delContainer") {
            errCode = gDevice.syncDelContainer(gDevId, "app", "cont");
            CHECK(errCode == 0);
        }

        SUBCASE("delApp") {
            errCode = gDevice.syncDelApp(gDevId, "app");
            CHECK(errCode == 0);
        }
    } else {
        errCode = gSignx.syncDupCertWithTemplate(gDevId, APPNAME, CONNAME, "1");
        CHECK(errCode == 0);

        errCode = gSignx.syncDupCertWithTemplate(gDevId, APPNAME, CONNAME, "0");
        CHECK(errCode == 0);
    }
}

TEST_CASE("parseCert") {
    std::string cert =
        "MIIByzCCAXCgAwIBAgIMXeEAAAAAAAAAAAA/"
        "MAwGCCqBHM9VAYN1BQAwIzELMAkGA1UEBhMCQ04xFDASBgNVBAMMC2xvY2FsX2NhX3NtMB4XDTE5MTAzMDE2MDAwMFoXDTIyMTAzMDE1NTk1OVowJTELMAkGA1UEBhMCQ04xFjAUBgNV"
        "BAMMDW9ubGluZXRlc3RubzEwWTATBgcqhkjOPQIBBggqgRzPVQGCLQNCAAR4At/a3kaV5HvTdjBQIltUBKtSKscbTf5CgqGTg7LRyqBwlJExJChUHBcOp6scfD/"
        "AOPQ+dQx2fFys7d2+"
        "aC3Ao4GFMIGCMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcDBDAOBgNVHQ8BAf8EBAMCAMAwEQYJYIZIAYb4QgEBBAQDAgCAMB8GA1UdIwQYMBaAFP3yRXsMuZnwQo7er8LzivjK"
        "8kuRMB0GA1UdDgQWBBTc1o7/33X/bOQlyWAHsyfrdr78bDAMBggqgRzPVQGDdQUAA0cAMEQCIC+gzN+MdZ0N7UT2bBYQr3zIEJSNpC/"
        "BrJdNcqk3l46qAiBfmAtGAtQBSKRC7V8CZrl2H+Kuwnwf24fYp8LMuaVqsQ==";
    errCode = gSignx.syncParseCert(cert);
    CHECK(errCode == 0);
};

TEST_CASE("import pfx for test 'envelopeEncrypt/envelopeDecrypt'") {
    std::string strAppName = "";
    if (isKoalSoft) {
        strAppName = "app";
        SUBCASE("createApp") {
            errCode = gDevice.syncCreateApp(gDevId, strAppName, "admin", 10, "1qaz!QAZ", 10, 255);
            CHECK(errCode == 0);
        }
        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, strAppName, 1, "1qaz!QAZ");
            CHECK(errCode == 0);
        }
    } else {
        strAppName = APPNAME;
        SUBCASE("verifyPIN") {
            errCode = gDevice.syncVerifyPIN(gDevId, strAppName, 1, PINCODE);
            CHECK(errCode == 0);
        }
    }

    SUBCASE("createContainer") {
        errCode = gDevice.syncCreateContainer(gDevId, strAppName, "rsa");
        CHECK(errCode == 0);
    }

    SUBCASE("createContainer") {
        errCode = gDevice.syncCreateContainer(gDevId, strAppName, "ecc");
        CHECK(errCode == 0);
    }

    SUBCASE("ECC") {
        SUBCASE("genKeypair") {
            errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "ecc", "0", "2048", 1);
            CHECK(errCode == 0);
        }

        SUBCASE("importPfxCert") {
            std::string eccPfxStr =
                "MIIEEgIBAzCCA9gGCSqGSIb3DQEHAaCCA8kEggPFMIIDwTCCArcGCSqGSIb3DQEHBqCCAqgwggKkAgEAMIICnQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIKOPAUKhj"
                "zAgCAggAgIICcFQRSy/C6EvDqMdWJyGKmxvzCIJEqPdQnzc8NqRvxqJc0/2EZymOOpVihv1sl2YzIcPlrjD8m86+72hvGToW+Fv35ZDvoDmml5HKFb/"
                "AhMwe27QwGo73p7v6yTjkLzSz4JdUv6gRTUnJUiVbcJuGN2qS4ByyjEw10vCu6lRKNgOwrv6Krt0U6W+0Bxc78VUpYkxc95jOA/"
                "F6uOaSGZAosLL+biXc8GOZ0meZz92NUqWDkzySFHzi7qL6UcFJ6meT09Nc3K6SeJfKACWlXclNka7wg5pMWrw21cGsRGo3xyTeQME4tFTWWieHFnGvjo/"
                "tuouRPdiuYWMNJj5JcGGPL7uH8w/mnJPaero6DOCrMf8o4p/IrShkH3njNHzeolEu652yHRZnP/hGTSee5/XETRX0CTHrljyx2Lcp7w+dyRC5dIGTwvUk/mTFA5C/"
                "Yf775OwO+"
                "45gEjp6wEMZzs2kNUHj9cLt7D7EEZYJeaZLeSgObsrMgvGaR1xWMkVIcr9l9rpzV2Qtb4svE5uQy3WHrR4MbnQjK9MMTzR2CSnLjKlderqNWT2HFUnp1hXix6bBK7TjWQu+"
                "J+p1KFtr/ss5xahJbyeWIasSqj5Aoduugzq+wMLLBhC/DzXWRgwcVJJ6vtAPSx3nPH2aliKv1VheoIsfza+0jUWR/"
                "kydCCHNyIQ75tXdiqlMqcREj4u13Gjsq83Sn5Mf5iJcp1tMXKFebDgqtHa8BPN+oz0yfTMWZ6eGmr5s0H6fl6fbr+KrFG3UMAoQL7/"
                "0jLXPpXZpmgWROX5QL5iJuOkmAVdQAx+"
                "BDsjhu3v8wqqGZfXG8Xxi7dukZTCCAQIGCSqGSIb3DQEHAaCB9ASB8TCB7jCB6wYLKoZIhvcNAQwKAQKggbQwgbEwHAYKKoZIhvcNAQwBAzAOBAhMkZAWBq4B/"
                "QICCAAEgZAPkzeWCH8ZIRMn1yIGaVMLeGMafQ+ADypKJS/IuPQP58tnvh3udIDJ04ZuH6QsXXzr4xOfBaTHRe/M9ZvKoRkkx7a9PgCaxdo9IN40/"
                "1bqBeOvVNLrHghPVx5rTYhWDUA/+PkEDAnYXvTuObbJr8Lph0le6AHMlDZXtHl7m/"
                "XDPZ2NqVjVEN0dQwotcelCaJExJTAjBgkqhkiG9w0BCRUxFgQU8xfDWkJHSbCMuP6dl1INzjODK/8wMTAhMAkGBSsOAwIaBQAEFNsS6yoBrJXQAIEU/"
                "60AjQn7eAtfBAiFJYY31zOvHwICCAA=";

            errCode = gEnRoll.syncImportPfxCert(gDevId, strAppName, "ecc", eccPfxStr, "123456");
            CHECK(errCode == 0);
        }
        SUBCASE("envelopeEncrypt/envelopeDecrypt") {
            SUBCASE("envelopeEncrypt") {
                std::string cert =
                    "MIICETCCAbigAwIBAgIGIBkFCAAFMAoGCCqBHM9VAYN1MFsxCzAJBgNVBAYTAkNOMQ8wDQYDVQQIDAZzaGFueGkxDTALBgNVBAcMBHhpYW4xDTALBgNVBAoMBGtvYWwx"
                    "CzAJBgNVBAsMAmNhMRAwDgYDVQQDDAdlY2NSb290MB4XDTE5MTAyMDEyMjIxNFoXDTI5MTAxNzEyMjIxNFowOzELMAkGA1UEBhMCQ04xDzANBgNVBAgMBnNoYW54aTEN"
                    "MAsGA1UECgwEa29hbDEMMAoGA1UEAwwDempqMFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEFlQQtdVN0r0EJe9CP1KWHCUWbNwKW0itzGqR9zPB3wkjotxG0ITco9V0"
                    "zWpsqOLgfDqsUvQw+"
                    "YgPyH7fvdIQEqOBhzCBhDAJBgNVHRMEAjAAMAsGA1UdDwQEAwIFIDAqBglghkgBhvhCAQ0EHRYbR21TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBSuR"
                    "uprzbPKZmtP/"
                    "k71BvKn9yxyUTAfBgNVHSMEGDAWgBStNt6lbdm0B8T9oeLvpo84hqmvyTAKBggqgRzPVQGDdQNHADBEAiALsovOfj0FVLEkJ+"
                    "ZCfCAXeKrenU2NyP1xsYOGysd61wIgHhzR/iXD43KRCCGUva7lfIcjSE6/fVIUXHT+6++Yg3k=";
                errCode = gSignx.syncEnvelopeEncrypt("MTIzNDU2NzgxMjM0NTY3OA==", cert, 3);
                CHECK(errCode == 0);
            }
            SUBCASE("envelopeDecrypt") {
                errCode = gSignx.syncEnvelopeDecrypt(gDevId, strAppName, "ecc", gSignx.syncGetEnvelopeEncryptData());
                CHECK(errCode == 0);
            }
        }
    }

    SUBCASE("RSA") {
        SUBCASE("genKeypair") {
            errCode = gEnRoll.syncGenKeypair(gDevId, strAppName, "rsa", "1", "2048", 1);
            CHECK(errCode == 0);
        }

        SUBCASE("importPfxCert") {
            std::string rsaPfxStr =
                "MIIJYQIBAzCCCScGCSqGSIb3DQEHAaCCCRgEggkUMIIJEDCCA8cGCSqGSIb3DQEHBqCCA7gwggO0AgEAMIIDrQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIO1cTp9Qm"
                "g1cCAggAgIIDgEdIvau0WxxSU1G7DiwupoZX2OmOGuG8voZWMh4yGcs0mUGzgymaBqySpWDqKySbTro7JWa8PKjoHfm6mKUwKnPLruoZc9SYTIRfzpSC4AEU8jMRmLOKSNIf"
                "qqn0bhNwH8yFLkwPlQRhqvmDw3LmCzANP829v6iAHzlDnzfYrG+XdtDs399N99eJzbEyulwI8zYXCpFSdg3EFIO0nFy0k55fccg3+"
                "MvEdoUADmKukwwzCk1tpARU0xokIFpyTrcU1GF2gCLLCo9oSlaolLbh7JXLE+55Vd/URaGHQxjCqVMc5RkV7tOWXtPpjPBDCRdI2MNwd/"
                "eYMeHoxPF3ih2rNOLV4me8WxhLIJpqix1F0PEmIlMqPxuyA7svZxeAoUQcbIV/G4+7bt/14YvIAT39GjpyiiQU2cG/l49PgNYJMIFWKY/agW+nVN8QDpnX9m6yh/"
                "LzMcGgGopfA8rFn3n6ZOfjytScVxn/GvrSekQYqRm7hvqJo5SyT1ceGv0DvBMiuDyoewzRcWMV/YwnaW1khOHAgymlQ+3WmEEpG2m+VYgmnelL8lNPd1k0b+kh6LzUjWVj/"
                "jxMQvALkYYYBnV4YEIeSzcRIMKPovfVIMLFN+RAOHVUEfpyJg9dogOPgR0nIpLmbm8svSK8KIVT2yNFxrI7nubvBW0ybWqP0THZjRiv/"
                "Aimgb628EmJSL19LBksylvkpvRmY38rUdJ3zVxAijz9kwq5LfptYBiNoMKM+i0pnwLAoz3Aa+gXdqFrC6AlRoXmnWrtG1G8Ls8N4W6M4l8quz+0bGdxBQ+"
                "XPaZjAG2FwS2Sw8xDvVPgfc6ss+tvvuzVs23h9RnYXTgLqSm1ngOJrKti1pRr+ayBh8aM993FZqaUDVy1hM7RODPpHBQF9KBHEszuLnp4MqTwRtn7GivQ/"
                "Fsawk9pAhQLssyL1LvX+Em96jRshAqkb4KXUa6AJpLjNIFhCgsu5XamOC6u5JjHJK8D5PdtaMapU8PDt4w071D5QDFe8XPQcWxvJ1EgyxyrrgeVXqXiW9fy4TY5AjSK4qe/"
                "CW5Ae/NsAkzNzgRBm/"
                "mWHmIEFcDVXBwq8iayc4zi+ebXFDqMDlRyhm3RDkxcM5daIQhz2Ju7rIpbJX8Baay5DK1DCm3D+"
                "PY5NnAfpswQdcf28uyuOyjyEfMcgOabdRMwdNpaVKu4SyrxKPulMIIFQQYJKoZIhvcNAQcBoIIFMgSCBS4wggUqMIIFJgYLKoZIhvcNAQwKAQKgggTuMIIE6jAcBgoqhkiG9"
                "w0BDAEDMA4ECHTjLAeT99mFAgIIAASCBMjflyOyXYKyt8qos/"
                "TMhZ4PX7J2FnfToq0D5cniGz246LLEKytHPzyC7afdYx831MirZvZ05Z1V0sWsnBvXuoqPb2RQKLx5aZfDfEZ1moh1LV8H906aBkxaLA5ioA9xQAd1E33R/"
                "S7k5uLhTeqwDRPGWV9/hHt4tbPZdDkk/"
                "oFzKTuQL3sNfwjEzqqWdh4cJDJcfLbgcB8sIB8zIaYrACAR4eBOAn0cBFJgPIwdh5IaHYa6cXUK4U9b5zeYKkQpBf2fdPynRggv14ya2nEIXdR4eXGm33SUZIbTb0AyjlHaH"
                "Pb8HvD7jG9nBvrA/"
                "HfsYM4TsPq3vwNlpafk+NnDkGaFlHDBI0AlkDdGwhhOEtVx5MZ5TUXPtyFR0B6jwM4yTuBPWW10purPviUi0+ymBHfAJSEzW3HESLZnn2e6OKjMSJ5gOVgO/"
                "Oo7WeTs7f6P8MytJTdzC8IUqRfobmu+VnBykObPKtPq/"
                "eLt7QZeGLI6nbMQoswYBTVhQx3cwut4M8ZgblEQ2fSk5T3M9F52YXEjLwW9zkiZKjN7Fkr+DgL8CQWEVpUh0NoRqrFYoKX/fyQyn/"
                "CQdQNG1Cuv5WSN5HLIT287bx80Qa65Pm7oOxY2tfZmRg+ZYE9LJAEt4/"
                "Ru26ilfHR9fx19OQyijgndXL+Nn1PW4PkpzRKOLuxZPbByDCxcC7wgsaoLx7xjPDFU5WOq0fvJ2k6UaoVhwnV+VrEA/3C9Nj04XlDgI9YUkb17f1pUA/"
                "qBOTwTgiU8ajwcZdV/"
                "E9Wu+9m3Q531MEJ3ZHUGZ7GEWp7NdXDGbvRUJXoT0gQNdVESc8RGFia8rgFN5bloXQLmk6Yb1oLCTxP9pf89JsiCap8GmqWMngoZzfCtd8nNjfEupopJJXQuZ+"
                "d0j0ePXUivK8kXV8unsl3t3VVtM2m+D5C0fNIDsb//swUUHrieILVyGOESCAqgCok1o0p+FUYuhrMqL5oSKAN9cis0uHW5ZgkC9FxKLczTJN/"
                "aknhT6Re0zTqoAvUpUxWQMxytKNCmtuBuXem2mvPQk+X6gXHnuStv7181KPAyHUJwNVBrpY3JUVktOSkt+TNvi9hBFP5gdo1nZNkcA2B/hC/"
                "x5UH9sHl3Q9E7bPyQf2TUBf1Uogihub/V326wjMRz2dSc1ojD/rqHVk1yLyJGCz/"
                "iFZZIjp12w7hGyPvOqrVVIJrlPDcqEohUDBwp56Da1AfmWDzWuhKuTjMOY77ge15JR6CLiDK7xbNSppOhdcaxgKPMOmmtinbH94hPrtBYXhgRcU/"
                "ZCZQ34Q48Kjmo4LRJPOifCaX1HsL2D8z85qwB9Fe5iQnubsE8UBpi7ucYEZt1sd5jLLeVPbiavaYcCtWn7D4dToCp2c6hhG9s2L0pY+YRVa4imexUIIdKmfD2hB39NuADbA/"
                "aKX9rrSI34MfGGwZcKwJ76Wp3n9mo9xHqAnm6QwXpNIxvqhHVD7ccCi2Kme9JMZFzaW8Ue5xJQTeoJrG5iH/shd/"
                "vFNbZsu9n4sZ3KuhoOEd+oMB7AYZWa7BDYkiQTd62z0CVhj8pAw18k+/i314LkCo4O6hZCnX+9y/QsZoBJ+ohEU9e+gI+c/r83iC6GhJKRj2ycx/"
                "8cCwPPGS33LHCn3cxJTAjBgkqhkiG9w0BCRUxFgQUbRjsUyyfanyD3fcz/"
                "etuBwHXcOUwMTAhMAkGBSsOAwIaBQAEFKw31nAlRW7tugM9friGjnYkRmviBAjwMBhsIOs9yAICCAA=";

            errCode = gEnRoll.syncImportPfxCert(gDevId, strAppName, "rsa", rsaPfxStr, "123456");
            CHECK(errCode == 0);
        }

        if (isKoalSoft)  // longmai测试key只支持sm4
        {
            SUBCASE("envelopeEncrypt") {
                std::string cert =
                    "MIIDIzCCAgsCFH5D56IHCP/7r/"
                    "Rhlunz9BTJ2kzbMA0GCSqGSIb3DQEBCwUAME4xCzAJBgNVBAYTAkNOMQ8wDQYDVQQIDAZzaGFueGkxDTALBgNVBAcMBHhpYW4xDTALBgNVBAoMBGtvYWwxEDAOBgNVBA"
                    "MMB3JzYVJvb3QwHhcNMTkxMTA2MDU0NjI4WhcNMjkxMTAzMDU0NjI4WjBOMQswCQYDVQQGEwJDTjEPMA0GA1UECAwGc2hhbnhpMQ0wCwYDVQQHDAR4aWFuMQ0wCwYDVQ"
                    "QKDARrb2FsMRAwDgYDVQQDDAdyc2FUZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0/"
                    "qOnKCLPmDzfPo7Uvkm5j7SFr5SghYKlXdVUtTkTTMczpQ2D1OyV4BSvLYG8R3Os78Emq2EhZxk0H0eMDloV6/"
                    "0NYFHChKxB4+5yfbA5yUSfqBhLKsn4zKwT09dm7ZTgmL9zTsqIRrFIHFkamcWDd/xt/"
                    "F7tq5jWeAgu2WO3JRp0WvvIy2IoPqodU7JRZYEkA+dpuo1rtbQDNAkF309NtbWCTD9/"
                    "9Eo386lMCpa1Rk4EBCfOBMVPj8RsGUYpCNxBqXMGIBqOiPEN6alA8eZeE5Q7NbdFP6kA46tHtWXezpo8E1sWAFo8xmZHY/"
                    "anyTM4b2mo5oTB7xsfDfo55xIAwIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQC+n7JDmH960VeaH7L8BYNAKcFfecqZk0azx72lww6v35xt9FP5IJRQI1KHAm/S/"
                    "sdyqWANlPIg2dvXkGgXnn/R5n2wRb5uJhrUGWd+xqU9gqxjlL1oDsLONZB5O3kcTLcugVM2fqAMD/"
                    "PY4gZ+mdSvQN+MINOf+tm039zcwFmD4MUPJvZmIJg9emNXCwpiGoS7LtK/"
                    "OgRQhuUznG41xq2b5XqoeskoccdGG+ZOM6NnfKD1+"
                    "7btRflpVVkoq45y57FfdAwodN2HahHeXoDvkNDONHv3VTn6LrLy9DfY42l977U5XgLI8dW66jNeLMsymohaqPwKwPrCrfKOV8hKmppr";
                errCode = gSignx.syncEnvelopeEncrypt("MTIzNDU2NzgxMjM0NTY3OA==", cert, 1);
                CHECK(errCode == 0);
            }
            SUBCASE("envelopeDecrypt") {
                errCode = gSignx.syncEnvelopeDecrypt(gDevId, strAppName, "rsa", gSignx.syncGetEnvelopeEncryptData());
                CHECK(errCode == 0);
            }

            SUBCASE("envelopeEncrypt") {
                std::string cert =
                    "MIIDIzCCAgsCFH5D56IHCP/7r/"
                    "Rhlunz9BTJ2kzbMA0GCSqGSIb3DQEBCwUAME4xCzAJBgNVBAYTAkNOMQ8wDQYDVQQIDAZzaGFueGkxDTALBgNVBAcMBHhpYW4xDTALBgNVBAoMBGtvYWwxEDAOBgNVBA"
                    "MMB3JzYVJvb3QwHhcNMTkxMTA2MDU0NjI4WhcNMjkxMTAzMDU0NjI4WjBOMQswCQYDVQQGEwJDTjEPMA0GA1UECAwGc2hhbnhpMQ0wCwYDVQQHDAR4aWFuMQ0wCwYDVQ"
                    "QKDARrb2FsMRAwDgYDVQQDDAdyc2FUZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0/"
                    "qOnKCLPmDzfPo7Uvkm5j7SFr5SghYKlXdVUtTkTTMczpQ2D1OyV4BSvLYG8R3Os78Emq2EhZxk0H0eMDloV6/"
                    "0NYFHChKxB4+5yfbA5yUSfqBhLKsn4zKwT09dm7ZTgmL9zTsqIRrFIHFkamcWDd/xt/"
                    "F7tq5jWeAgu2WO3JRp0WvvIy2IoPqodU7JRZYEkA+dpuo1rtbQDNAkF309NtbWCTD9/"
                    "9Eo386lMCpa1Rk4EBCfOBMVPj8RsGUYpCNxBqXMGIBqOiPEN6alA8eZeE5Q7NbdFP6kA46tHtWXezpo8E1sWAFo8xmZHY/"
                    "anyTM4b2mo5oTB7xsfDfo55xIAwIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQC+n7JDmH960VeaH7L8BYNAKcFfecqZk0azx72lww6v35xt9FP5IJRQI1KHAm/S/"
                    "sdyqWANlPIg2dvXkGgXnn/R5n2wRb5uJhrUGWd+xqU9gqxjlL1oDsLONZB5O3kcTLcugVM2fqAMD/"
                    "PY4gZ+mdSvQN+MINOf+tm039zcwFmD4MUPJvZmIJg9emNXCwpiGoS7LtK/"
                    "OgRQhuUznG41xq2b5XqoeskoccdGG+ZOM6NnfKD1+"
                    "7btRflpVVkoq45y57FfdAwodN2HahHeXoDvkNDONHv3VTn6LrLy9DfY42l977U5XgLI8dW66jNeLMsymohaqPwKwPrCrfKOV8hKmppr";
                errCode = gSignx.syncEnvelopeEncrypt("MTIzNDU2NzgxMjM0NTY3OA==", cert, 2);
                CHECK(errCode == 0);
            }
            SUBCASE("envelopeDecrypt") {
                errCode = gSignx.syncEnvelopeDecrypt(gDevId, strAppName, "rsa", gSignx.syncGetEnvelopeEncryptData());
                CHECK(errCode == 0);
            }
        }

        SUBCASE("envelopeEncrypt") {
            std::string cert =
                "MIIDIzCCAgsCFH5D56IHCP/7r/"
                "Rhlunz9BTJ2kzbMA0GCSqGSIb3DQEBCwUAME4xCzAJBgNVBAYTAkNOMQ8wDQYDVQQIDAZzaGFueGkxDTALBgNVBAcMBHhpYW4xDTALBgNVBAoMBGtvYWwxEDAOBgNVBAMMB3"
                "JzYVJvb3QwHhcNMTkxMTA2MDU0NjI4WhcNMjkxMTAzMDU0NjI4WjBOMQswCQYDVQQGEwJDTjEPMA0GA1UECAwGc2hhbnhpMQ0wCwYDVQQHDAR4aWFuMQ0wCwYDVQQKDARrb2"
                "FsMRAwDgYDVQQDDAdyc2FUZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0/"
                "qOnKCLPmDzfPo7Uvkm5j7SFr5SghYKlXdVUtTkTTMczpQ2D1OyV4BSvLYG8R3Os78Emq2EhZxk0H0eMDloV6/"
                "0NYFHChKxB4+5yfbA5yUSfqBhLKsn4zKwT09dm7ZTgmL9zTsqIRrFIHFkamcWDd/xt/"
                "F7tq5jWeAgu2WO3JRp0WvvIy2IoPqodU7JRZYEkA+dpuo1rtbQDNAkF309NtbWCTD9/"
                "9Eo386lMCpa1Rk4EBCfOBMVPj8RsGUYpCNxBqXMGIBqOiPEN6alA8eZeE5Q7NbdFP6kA46tHtWXezpo8E1sWAFo8xmZHY/"
                "anyTM4b2mo5oTB7xsfDfo55xIAwIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQC+n7JDmH960VeaH7L8BYNAKcFfecqZk0azx72lww6v35xt9FP5IJRQI1KHAm/S/"
                "sdyqWANlPIg2dvXkGgXnn/R5n2wRb5uJhrUGWd+xqU9gqxjlL1oDsLONZB5O3kcTLcugVM2fqAMD/"
                "PY4gZ+mdSvQN+MINOf+tm039zcwFmD4MUPJvZmIJg9emNXCwpiGoS7LtK/"
                "OgRQhuUznG41xq2b5XqoeskoccdGG+ZOM6NnfKD1+"
                "7btRflpVVkoq45y57FfdAwodN2HahHeXoDvkNDONHv3VTn6LrLy9DfY42l977U5XgLI8dW66jNeLMsymohaqPwKwPrCrfKOV8hKmppr";
            errCode = gSignx.syncEnvelopeEncrypt("MTIzNDU2NzgxMjM0NTY3OA==", cert, 3);
            CHECK(errCode == 0);
        }
        SUBCASE("envelopeDecrypt") {
            errCode = gSignx.syncEnvelopeDecrypt(gDevId, strAppName, "rsa", gSignx.syncGetEnvelopeEncryptData());
            CHECK(errCode == 0);
        }
    }

    SUBCASE("delContainer") {
        errCode = gDevice.syncDelContainer(gDevId, strAppName, "rsa");
        CHECK(errCode == 0);
    }
    SUBCASE("delContainer") {
        errCode = gDevice.syncDelContainer(gDevId, strAppName, "ecc");
        CHECK(errCode == 0);
    }

    if (isKoalSoft) {
        SUBCASE("delApp") {
            errCode = gDevice.syncDelApp(gDevId, strAppName);
            CHECK(errCode == 0);
        }
    }
}

#endif  //_TESTCASE_HPP_
