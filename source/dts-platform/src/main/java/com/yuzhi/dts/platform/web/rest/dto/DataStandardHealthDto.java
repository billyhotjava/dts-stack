package com.yuzhi.dts.platform.web.rest.dto;

public class DataStandardHealthDto {
    private boolean ok;
    private String message;
    private boolean keyConfigured;
    private boolean keyValid;
    private int keyBytes;
    private String storageStrategy;
    private String storageDir;
    private Boolean storageDirWritable;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isKeyConfigured() { return keyConfigured; }
    public void setKeyConfigured(boolean keyConfigured) { this.keyConfigured = keyConfigured; }
    public boolean isKeyValid() { return keyValid; }
    public void setKeyValid(boolean keyValid) { this.keyValid = keyValid; }
    public int getKeyBytes() { return keyBytes; }
    public void setKeyBytes(int keyBytes) { this.keyBytes = keyBytes; }
    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }
    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
    public Boolean getStorageDirWritable() { return storageDirWritable; }
    public void setStorageDirWritable(Boolean storageDirWritable) { this.storageDirWritable = storageDirWritable; }
}

