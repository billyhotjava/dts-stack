package com.yuzhi.dts.ingestion.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sprint-37 M1/M3：验证涉密文件加密的「禁止明文回退」与密钥长度 fail-fast。
 */
class InfraSettingsCryptoServiceTest {

    private static final String KEY_256 = Base64
        .getEncoder()
        .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private InfraSettingsCryptoService withKey(String base64Key, String version) {
        InfraSecurityProperties props = new InfraSecurityProperties();
        props.setEncryptionKey(base64Key);
        props.setKeyVersion(version);
        InfraSettingsCryptoService svc = new InfraSettingsCryptoService(props);
        svc.init();
        return svc;
    }

    private InfraSettingsCryptoService noKey() {
        InfraSettingsCryptoService svc = new InfraSettingsCryptoService(new InfraSecurityProperties());
        svc.init();
        return svc;
    }

    @Test
    @DisplayName("encryptStrict/decryptStrict 往返一致(有密钥)")
    void strict_roundTrip() throws Exception {
        InfraSettingsCryptoService svc = withKey(KEY_256, "v1");
        byte[] iv = svc.randomIv();
        byte[] plain = "涉密内容".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = svc.encryptStrict(plain, iv);

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(svc.decryptStrict(cipher, iv)).isEqualTo(plain);
    }

    @Test
    @DisplayName("encryptStrict 密钥缺失时抛异常，绝不明文回退")
    void encryptStrict_missingKey_throws() {
        InfraSettingsCryptoService svc = noKey();

        assertThat(svc.isEncryptionReady()).isFalse();
        assertThatThrownBy(() -> svc.encryptStrict("x".getBytes(StandardCharsets.UTF_8), svc.randomIv()))
            .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("decryptStrict 密钥缺失时抛异常，绝不返回密文原文")
    void decryptStrict_missingKey_throws() {
        InfraSettingsCryptoService svc = noKey();

        assertThatThrownBy(() -> svc.decryptStrict("x".getBytes(StandardCharsets.UTF_8), svc.randomIv()))
            .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("encrypt(通用) 密钥缺失时保留明文回退语义(InfraSettings 回归保护)")
    void encrypt_missingKey_fallsBackToPlaintext() throws Exception {
        InfraSettingsCryptoService svc = noKey();
        byte[] plain = "settings".getBytes(StandardCharsets.UTF_8);

        assertThat(svc.encrypt(plain, svc.randomIv())).isEqualTo(plain);
    }

    @Test
    @DisplayName("init 非法长度密钥(15B) fail-fast")
    void init_invalidKeyLength_throws() {
        String key15 = Base64.getEncoder().encodeToString("0123456789abcde".getBytes(StandardCharsets.UTF_8));
        InfraSecurityProperties props = new InfraSecurityProperties();
        props.setEncryptionKey(key15);
        InfraSettingsCryptoService svc = new InfraSettingsCryptoService(props);

        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("16/24/32");
    }

    @Test
    @DisplayName("init 非法 Base64 fail-fast")
    void init_invalidBase64_throws() {
        InfraSecurityProperties props = new InfraSecurityProperties();
        props.setEncryptionKey("!!!not-base64!!!");
        InfraSettingsCryptoService svc = new InfraSettingsCryptoService(props);

        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("合法 32 字节密钥 init 后加密就绪")
    void init_validKey_ready() {
        assertThat(withKey(KEY_256, "v1").isEncryptionReady()).isTrue();
    }
}
