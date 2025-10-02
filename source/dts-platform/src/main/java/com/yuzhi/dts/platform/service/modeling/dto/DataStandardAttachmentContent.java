package com.yuzhi.dts.platform.service.modeling.dto;

public class DataStandardAttachmentContent {

    private final byte[] data;
    private final String fileName;
    private final String contentType;

    public DataStandardAttachmentContent(byte[] data, String fileName, String contentType) {
        this.data = data;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public byte[] getData() {
        return data;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }
}

