package com.yuzhi.dts.platform.service.modeling;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AttachmentSignatureValidator {

    private static final byte[] SIGN_EXE = {0x4D, 0x5A}; // MZ
    private static final byte[] SIGN_ZIP_STANDARD = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] SIGN_ZIP_EMPTY = {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] SIGN_ZIP_SPANNED = {0x50, 0x4B, 0x07, 0x08};
    private static final byte[] SIGN_COMPOUND = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] SIGN_PDF = {0x25, 0x50, 0x44, 0x46}; // %PDF

    private static final Set<String> ZIP_EXTENSIONS = Set.of("docx", "xlsx", "wps");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xls",
        "application/vnd.ms-excel",
        "pdf",
        "application/pdf",
        "txt",
        "text/plain; charset=utf-8",
        "md",
        "text/markdown; charset=utf-8",
        "wps",
        "application/vnd.ms-works"
    );

    private AttachmentSignatureValidator() {}

    static String validate(String extension, byte[] data) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("文件内容为空，请重新上传");
        }
        if (startsWith(data, SIGN_EXE)) {
            throw new IllegalArgumentException("检测到可执行文件内容，禁止上传");
        }
        if ("pdf".equals(normalized)) {
            requireStartsWith(data, SIGN_PDF, "pdf");
            return MIME_BY_EXTENSION.get("pdf");
        }
        if ("xls".equals(normalized)) {
            requireCompound(data, "xls");
            return MIME_BY_EXTENSION.get("xls");
        }
        if ("wps".equals(normalized)) {
            if (isZip(data) || isCompound(data)) {
                return MIME_BY_EXTENSION.get("wps");
            }
            throw mismatch("wps");
        }
        if (ZIP_EXTENSIONS.contains(normalized)) {
            requireZip(data, normalized);
            return MIME_BY_EXTENSION.getOrDefault(normalized, "application/zip");
        }
        if (TEXT_EXTENSIONS.contains(normalized)) {
            requireText(data, normalized);
            return MIME_BY_EXTENSION.getOrDefault(normalized, "text/plain; charset=utf-8");
        }
        return MIME_BY_EXTENSION.getOrDefault(normalized, "application/octet-stream");
    }

    private static void requireZip(byte[] data, String ext) {
        if (!isZip(data)) {
            throw mismatch(ext);
        }
    }

    private static void requireCompound(byte[] data, String ext) {
        if (!isCompound(data)) {
            throw mismatch(ext);
        }
    }

    private static void requireStartsWith(byte[] data, byte[] signature, String ext) {
        if (!startsWith(data, signature)) {
            throw mismatch(ext);
        }
    }

    private static void requireText(byte[] data, String ext) {
        int length = Math.min(data.length, 8192);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data, 0, length));
        } catch (CharacterCodingException ex) {
            throw mismatch(ext);
        }
    }

    private static boolean isZip(byte[] data) {
        return startsWith(data, SIGN_ZIP_STANDARD) || startsWith(data, SIGN_ZIP_EMPTY) || startsWith(data, SIGN_ZIP_SPANNED);
    }

    private static boolean isCompound(byte[] data) {
        return startsWith(data, SIGN_COMPOUND);
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException mismatch(String ext) {
        String message = "文件内容与扩展名不匹配，请上传有效的 " + ext + " 文件";
        return new IllegalArgumentException(message);
    }
}
