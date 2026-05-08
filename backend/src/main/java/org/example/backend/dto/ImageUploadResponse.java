package org.example.backend.dto;

public class ImageUploadResponse {
    private String url;
    private String filename;
    private long size;

    public ImageUploadResponse(String url, String filename, long size) {
        this.url = url;
        this.filename = filename;
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
