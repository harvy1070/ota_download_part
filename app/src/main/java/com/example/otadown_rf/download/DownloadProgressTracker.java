package com.example.otadown_rf.download;

import android.util.Log;

import com.example.otadown_rf.callback.DownloadCallback;
import com.example.otadown_rf.utils.FileUtils;

/**
 * 다운로드 진행 상황을 추적하고 보고하는 클래스
 */
public class DownloadProgressTracker {
    private static final String TAG = DownloadProgressTracker.class.getSimpleName();

    private final DownloadCallback callback;
    private long totalBytes;
    private final long initialBytes;

    private long bytesReported;
    private long reportThreshold;

    /**
     * DownloadProgressTracker 생성자
     *
     * @param callback 다운로드 콜백 인터페이스
     * @param totalBytes 전체 파일 크기 (바이트)
     * @param initialBytes 이미 다운로드된 초기 바이트 수
     */
    public DownloadProgressTracker(DownloadCallback callback, long totalBytes, long initialBytes) {
        this.callback = callback;
        this.totalBytes = totalBytes <= 0 ? Long.MAX_VALUE : totalBytes; // 0대신 최소 1로 설정
        this.initialBytes = initialBytes;
        this.bytesReported = initialBytes;

        // 5% 단위로 진행 상황 보고를 위한 임계값 설정
        this.reportThreshold = totalBytes > 0 ? totalBytes / 20 : 8192; // 5% 또는 최소 8KB
    }

    public void updateTotalBytes(long totalBytes) {
        if (totalBytes > 0) {
            this.totalBytes = totalBytes;
            // 임계값도 다시 계산
            this.reportThreshold = this.totalBytes / 20;
        }
    }

    /**
     * 다운로드 시작을 보고
     */
    public void reportStart() {
        if (initialBytes > 0) {
            int progress = (int) (initialBytes * 100 / totalBytes);
            callback.onProgressUpdate(
                    progress,
                    "이어받기 시작 (" + FileUtils.formatFileSize(initialBytes) + "/" +
                            FileUtils.formatFileSize(totalBytes) + ")"
            );
        } else {
            callback.onProgressUpdate(0, "다운로드 시작 (총 " +
                    (totalBytes < Long.MAX_VALUE ? FileUtils.formatFileSize(totalBytes) : "알 수 없음") + ")");
        }
    }

    /**
     * 현재 진행 상황 업데이트
     *
     * @param currentBytes 현재까지 다운로드된 총 바이트 수
     * @return true: 진행 상황이 보고되었음, false: 보고 임계값에 도달하지 않음
     */
    public boolean updateProgress(long currentBytes) {
        if (totalBytes <= 0) return false;

        // 진행률 계산
        final int progress = (int) (currentBytes * 100 / totalBytes);
        final int roundedProgress = (progress / 5) * 5; // 5의 배수로 반올림

        // 보고 임계값 도달 여부 확인
        if (currentBytes - bytesReported >= reportThreshold) {
            bytesReported = currentBytes;

            Log.v(TAG, String.format("다운로드 진행 ▶ %d%% (%s / %s)",
                    roundedProgress,
                    FileUtils.formatFileSize(currentBytes),
                    FileUtils.formatFileSize(totalBytes)));

            callback.onProgressUpdate(roundedProgress, String.format("다운로드 진행 중 %d%% (%s / %s)",
                    roundedProgress,
                    FileUtils.formatFileSize(currentBytes),
                    FileUtils.formatFileSize(totalBytes)));

            return true;
        }

        return false;
    }

    /**
     * 다운로드 완료 보고
     *
     * @param duration 다운로드에 소요된 시간 (밀리초)
     * @param fileSize 다운로드된 파일의 크기
     */
    public void reportComplete(long duration, long fileSize) {
        String formattedTime = FileUtils.formatDownloadTime(duration);
        callback.onDownloadComplete("다운로드 완료 ▶ " + FileUtils.formatFileSize(fileSize) +
                " (소요 시간 ▶ " + formattedTime + ")");
    }

    /**
     * 다운로드 실패 보고
     *
     * @param errorMessage 오류 메시지
     */
    public void reportFailure(String errorMessage) {
        callback.onDownloadFailed(errorMessage);
    }

    /**
     * 다운로드 취소 보고
     *
     * @param cancelMessage 취소 메시지
     */
    public void reportCancellation(String cancelMessage) {
        callback.onDownloadCancelled(cancelMessage);
    }
}