package com.koal.h9y.svs.demo.util;

import com.sun.org.apache.xml.internal.security.utils.Base64;
import sun.misc.BASE64Encoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;

/**
 * @author: niuc
 * @Date: 2022-04-29
 * @Description:
 */
public class TestUtil {
    public TestUtil() {
    }

    public static String getCertInfo(String certValue, int type, String item) {
        String var3 = "";

        try {
            InputStream is = new ByteArrayInputStream(Base64.decode(certValue.getBytes()));
            X509Certificate x509Certificate = null;
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            x509Certificate = (X509Certificate)certificateFactory.generateCertificate(is);
            is.close();
            PublicKey publicKey = x509Certificate.getPublicKey();
            BASE64Encoder encoder = new BASE64Encoder();
            encoder.encode(publicKey.getEncoded());
            String dn;
            HashMap dns;
            if (type == 0) {
                dn = x509Certificate.getSubjectDN().toString();
                dns = getMapFromDN(dn);
                if ("DN".equals(item.toUpperCase())) {
                    return dn;
                }

                return (String)dns.get(item);
            }

            if (type == 1) {
                dn = x509Certificate.getIssuerDN().toString();
                dns = getMapFromDN(dn);
                if ("DN".equals(item.toUpperCase())) {
                    return dn;
                }

                return (String)dns.get(item);
            }

            if (type == 2) {
                return getExtFromOid(item, x509Certificate);
            }
        } catch (Exception var12) {
            var12.printStackTrace();
        }

        return "";
    }

    public static HashMap<String, String> getMapFromDN(String dn) {
        HashMap<String, String> result = new HashMap();
        String[] items = dn.trim().split(",");

        for(int i = 0; i < items.length; ++i) {
            String[] tempItem = items[i].split("=");
            result.put(tempItem[0], tempItem[1]);
        }

        return result;
    }

    public static String getExtFromOid(String oid, X509Certificate certificate) {
        String result = new String(certificate.getExtensionValue(oid));
        return result;
    }
}
