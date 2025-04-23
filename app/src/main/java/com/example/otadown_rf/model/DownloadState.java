package com.example.otadown_rf.model;

// SharedPreferences에 저장된 정보를 객체화하여 관리
public class DownloadState {
    private String downloadId;        // 다운로드 고유 ID
    private long downloadedBytes;     // 다운로드된 바이트 수
    private long totalBytes;          // 전체 파일 크기

    /**
     * 기본 생성자
     */
    public DownloadState() {
        this.downloadId = "";
        this.downloadedBytes = 0;
        this.totalBytes = 0;
    }

    /**
     * 다운로드 ID를 반환
     *
     * @return 다운로드 ID
     */
    public String getDownloadId() {
        return downloadId;
    }

    /**
     * 다운로드 ID를 설정
     *
     * @param downloadId 설정할 다운로드 ID
     */
    public void setDownloadId(String downloadId) {
        this.downloadId = downloadId;
    }

    /**
     * 현재까지 다운로드된 바이트 수를 반환
     *
     * @return 다운로드된 바이트 수
     */
    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    /**
     * 다운로드된 바이트 수를 설정
     *
     * @param downloadedBytes 설정할 다운로드된 바이트 수
     */
    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    /**
     * 전체 파일 크기를 반환
     *
     * @return 전체 파일 크기
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * 전체 파일 크기를 설정
     *
     * @param totalBytes 설정할 전체 파일 크기
     */
    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }
}
