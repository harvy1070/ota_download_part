package com.example.otadown_rf.callback;

public interface DownloadCallback {
    /**
     * 다운로드가 시작될 때 호출
     *
     * @param message 상태 메시지
     */
    void onDownloadStarted(String message);

    /**
     * 다운로드 진행 상황이 업데이트될 때 호출
     *
     * @param progress 진행률 (0-100)
     * @param message  상태 메시지
     */
    void onProgressUpdate(int progress, String message);

    /**
     * 다운로드가 완료되었을 때 호출
     *
     * @param message 완료 메시지
     */
    void onDownloadComplete(String message);

    /**
     * 다운로드가 실패했을 때 호출
     *
     * @param message 실패 이유
     */
    void onDownloadFailed(String message);

    /**
     * 다운로드가 취소되었을 때 호출
     *
     * @param message 취소 메시지
     */
    void onDownloadCancelled(String message);
}
