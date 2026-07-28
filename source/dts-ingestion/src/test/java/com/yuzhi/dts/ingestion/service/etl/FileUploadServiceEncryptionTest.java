package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Sprint-37 F1: 入湖上传文件 AES-GCM 加密存储。
 * 验证：加密往返、密文无明文魔数、元数据、表头内存解密、密钥缺失 fail-fast、cleanup 删 .enc。
 */
class FileUploadServiceEncryptionTest {

    @TempDir
    Path tempDir;

    private InfraSettingsCryptoService crypto;
    private CsvParseService csvParseService;

    private FileUploadService newService(InfraSettingsCryptoService cryptoSvc) {
        AddaxProperties addax = mock(AddaxProperties.class);
        when(addax.getJobDir()).thenReturn(tempDir.toString());
        IngestionSettingsService settings = mock(IngestionSettingsService.class);
        when(settings.getSettings(any()))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(java.util.Map.of("jobDir", tempDir.toString())));
        return new FileUploadService(addax, settings, csvParseService, cryptoSvc);
    }

    @BeforeEach
    void setUp() {
        InfraSecurityProperties props = new InfraSecurityProperties();
        props.setEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        props.setKeyVersion("v1");
        crypto = new InfraSettingsCryptoService(props);
        crypto.init();
        csvParseService = mock(CsvParseService.class);
    }

    private byte[] makeXlsx() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("数据");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("年龄");
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("张三");
            sample.createCell(1).setCellValue(30);
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @Test
    void upload_encryptsToEncFile_andRoundTrips() throws Exception {
        byte[] plain = makeXlsx();
        FileUploadService service = newService(crypto);

        var result = service.handleUpload(new MockMultipartFile("file", "people.xlsx", null, plain));

        // 落盘文件名以 .enc 结尾
        assertThat(result.hostPath()).endsWith(".enc");
        assertThat(result.containerPath()).endsWith(".enc");
        assertThat(result.encrypted()).isTrue();

        Path enc = Path.of(result.hostPath());
        byte[] stored = Files.readAllBytes(enc);

        // 布局 [verLen:1][keyVersion][IV:12][GCM 密文]；版本头自描述，其后解密还原 == 原文件
        int verLen = stored[0] & 0xFF;
        String ver = new String(stored, 1, verLen, StandardCharsets.UTF_8);
        assertThat(ver).isEqualTo("v1");
        int ivStart = 1 + verLen;
        byte[] iv = Arrays.copyOfRange(stored, ivStart, ivStart + 12);
        byte[] cipher = Arrays.copyOfRange(stored, ivStart + 12, stored.length);
        byte[] decrypted = crypto.decrypt(cipher, iv);
        assertThat(decrypted).isEqualTo(plain);

        // 密文不含 xlsx 的 ZIP 魔数 PK\x03\x04
        assertThat(indexOf(stored, new byte[] { 'P', 'K', 0x03, 0x04 })).isEqualTo(-1);
    }

    @Test
    void upload_writesEncryptionMetadata() throws Exception {
        byte[] plain = makeXlsx();
        FileUploadService service = newService(crypto);

        var result = service.handleUpload(new MockMultipartFile("file", "people.xlsx", null, plain));

        assertThat(result.keyVersion()).isEqualTo("v1");
        assertThat(result.encrypted()).isTrue();
        assertThat(result.originalName()).isEqualTo("people.xlsx");
        assertThat(result.fileSize()).isEqualTo((long) plain.length);
        // fileHash 必须是「明文」的摘要（供解密后完整性校验）
        assertThat(result.fileHash()).isEqualTo(sha256Hex(plain));
    }

    @Test
    void verifyManagedUpload_resolvesCanonicalEncryptedFileAndPlaintextChecksum() throws Exception {
        byte[] plain = makeXlsx();
        FileUploadService service = newService(crypto);
        var upload = service.handleUpload(
            new MockMultipartFile("file", "people.xlsx", null, plain)
        );

        FileUploadService.ManagedUpload verified = service.verifyManagedUpload(
            upload.fileId(),
            sha256Hex(plain)
        );

        assertThat(verified.fileId()).isEqualTo(upload.fileId());
        assertThat(verified.hostPath()).isEqualTo(Path.of(upload.hostPath()).toRealPath().toString());
        assertThat(verified.containerPath()).isEqualTo(upload.containerPath());
        assertThat(verified.fileHash()).isEqualTo(sha256Hex(plain));
    }

    @Test
    void verifyManagedUpload_rejectsChecksumThatDoesNotMatchDecryptedPayload() throws Exception {
        FileUploadService service = newService(crypto);
        var upload = service.handleUpload(
            new MockMultipartFile("file", "people.xlsx", null, makeXlsx())
        );

        assertThatThrownBy(() ->
            service.verifyManagedUpload(
                upload.fileId(),
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FILE_CHECKSUM_MISMATCH");
    }

    @Test
    void upload_eachUpload_usesUniqueIv() throws Exception {
        byte[] plain = makeXlsx();
        FileUploadService service = newService(crypto);

        byte[] s1 = Files.readAllBytes(Path.of(service.handleUpload(new MockMultipartFile("file", "a.xlsx", null, plain)).hostPath()));
        byte[] s2 = Files.readAllBytes(Path.of(service.handleUpload(new MockMultipartFile("file", "b.xlsx", null, plain)).hostPath()));

        int iv1Start = 1 + (s1[0] & 0xFF);
        int iv2Start = 1 + (s2[0] & 0xFF);
        assertThat(Arrays.copyOfRange(s1, iv1Start, iv1Start + 12))
            .isNotEqualTo(Arrays.copyOfRange(s2, iv2Start, iv2Start + 12));
    }

    @Test
    void upload_parsesExcelHeadersInMemory_noPlaintextOnDisk() throws Exception {
        byte[] plain = makeXlsx();
        FileUploadService service = newService(crypto);

        var result = service.handleUpload(new MockMultipartFile("file", "people.xlsx", null, plain));

        // 表头解析正确（解密自内存）
        assertThat(result.columns()).extracting(FileUploadService.FileColumn::label).contains("姓名", "年龄");
        // uploads 目录内只有 .enc，无任何明文文件
        try (var stream = Files.list(tempDir.resolve("uploads"))) {
            assertThat(stream.allMatch(p -> p.toString().endsWith(".enc"))).isTrue();
        }
    }

    @Test
    void upload_missingKey_failsFast_noFileWritten() throws Exception {
        InfraSecurityProperties noKey = new InfraSecurityProperties();
        InfraSettingsCryptoService disabled = new InfraSettingsCryptoService(noKey);
        disabled.init();
        FileUploadService service = newService(disabled);

        assertThatThrownBy(() -> service.handleUpload(new MockMultipartFile("file", "x.xlsx", null, makeXlsx())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("加密");

        Path uploads = tempDir.resolve("uploads");
        if (Files.exists(uploads)) {
            try (var stream = Files.list(uploads)) {
                assertThat(stream.findAny()).isEmpty();
            }
        }
    }

    @Test
    void parsesCsvHeaders_viaInMemoryStream() throws Exception {
        when(csvParseService.parseHeaders(any())).thenReturn(List.of(new FileUploadService.FileColumn("col_a", "string", "col_a")));
        byte[] plain = "col_a,col_b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        FileUploadService service = newService(crypto);

        var result = service.handleUpload(new MockMultipartFile("file", "data.csv", null, plain));

        assertThat(result.hostPath()).endsWith(".enc");
        assertThat(result.columns()).hasSize(1);
    }

    @Test
    void cleanupForTask_supports_filePath_aliases() throws Exception {
        FileUploadService service = newService(crypto);
        var upload = service.handleUpload(new MockMultipartFile("file", "people.xlsx", null, makeXlsx()));
        IngestionTask task = new IngestionTask();
        ObjectNode sourceConfig = new ObjectMapper().createObjectNode();
        sourceConfig.put("_filePath", upload.hostPath());
        task.setSourceConfig(sourceConfig);

        var deleted = service.cleanupForTask(task);

        assertThat(deleted).containsExactly(upload.hostPath());
        assertThat(Files.exists(Path.of(upload.hostPath()))).isFalse();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
