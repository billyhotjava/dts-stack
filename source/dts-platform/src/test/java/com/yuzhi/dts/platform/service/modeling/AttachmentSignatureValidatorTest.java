package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentSignatureValidatorTest {

    @Test
    void validateAcceptsDocxSignature() {
        byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        String mime = AttachmentSignatureValidator.validate("docx", docxBytes);
        assertThat(mime)
            .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void validateAcceptsPdfSignature() {
        byte[] pdfBytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        String mime = AttachmentSignatureValidator.validate("pdf", pdfBytes);
        assertThat(mime).isEqualTo("application/pdf");
    }

    @Test
    void validateRejectsExecutableMasqueradingAsDocx() {
        byte[] exeBytes = new byte[] {0x4D, 0x5A, 0x31, 0x32, 0x00, 0x00};
        assertThatThrownBy(() -> AttachmentSignatureValidator.validate("docx", exeBytes))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("禁止上传");
    }

    @Test
    void validateAcceptsUtf8TextFiles() {
        byte[] textBytes = "中文内容\nSecond Line".getBytes(StandardCharsets.UTF_8);
        String mime = AttachmentSignatureValidator.validate("txt", textBytes);
        assertThat(mime).isEqualTo("text/plain; charset=utf-8");
    }
}
