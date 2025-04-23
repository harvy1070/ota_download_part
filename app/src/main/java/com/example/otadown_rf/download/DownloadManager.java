package com.example.otadown_rf.download;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.otadown_rf.callback.DownloadCallback;
import com.example.otadown_rf.model.DownloadState;
import com.example.otadown_rf.model.DownloadStateManager;
import com.example.otadown_rf.network.ConnectionManager;
import com.example.otadown_rf.utils.FileUtils;

import java.io.File;
import java.util.UUID;

/**
 * 다운로드 프로세스 전체를 조율하는 클래스
 */
public class DownloadManager {
    private static final String TAG = DownloadManager.class.getSimpleName();
    private static final String DOWNLOAD_URL = "https://s3.ap-southeast-2.amazonaws.com/avn.directed.kr/firmware/TEST/random_file_1GB.bin";

    private final Context context;
    private final File downloadDir;
    private final DownloadCallback callback;
    private final DownloadStateManager stateManager;
    private final ConnectionManager connectionManager;

    private File downloadFile;
    private File tempFile;
    private DownloadTask downloadTask;
    private DownloadProgressTracker progressTracker;

    private long downloadStartTime;

    /**
     * DownloadManager 생성자
     *
     * @param context 앱 컨텍스트
     * @param downloadDir 다운로드 디렉토리
     * @param callback 다운로드 콜백 인터페이스
     */
    public DownloadManager(Context context, File downloadDir, DownloadCallback callback) {
        this.context = context;
        this.downloadDir = downloadDir;
        this.callback = callback;

        // 네트워크 연결 관리자 초기화
        this.connectionManager = new ConnectionManager();

        // 파일 경로 및 이름 설정
        downloadFile = new File(downloadDir, "update.bin");
        tempFile = new File(downloadDir, "update.bin.tmp");

        // 다운로드 상태 관리자 초기화
        stateManager = new DownloadStateManager(tempFile);
    }

    /**
     * 이전 다운로드 상태 확인
     */
    public void checkPreviousDownload() {
        DownloadState state = stateManager.loadState();
        if (state != null && state.getDownloadedBytes() > 0 && state.getTotalBytes() > 0 &&
                tempFile.exists() && tempFile.length() == state.getDownloadedBytes()) {
            int progress = (int) (state.getDownloadedBytes() * 100 / state.getTotalBytes());
            String message = String.format("이전 다운로드 발견 ▶ %d%% (%s / %s)",
                    progress,
                    FileUtils.formatFileSize(state.getDownloadedBytes()),
                    FileUtils.formatFileSize(state.getTotalBytes()));

            callback.onProgressUpdate(progress, message);
            Toast.makeText(context, "이전에 다운로드한 파일을 이어받을 수 있습니다.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 다운로드 시작
     */
    public void startDownload() {
        if (isDownloading()) {
            return;
        }

        downloadStartTime = System.currentTimeMillis();
        callback.onDownloadStarted("다운로드 준비 중...");

        try {
            // 현재 다운로드 상태 가져오기
            DownloadState state = stateManager.loadState();
            if (state == null) {
                state = new DownloadState();
                state.setDownloadId(UUID.randomUUID().toString());
            }

            // 이미 다운로드된 바이트 수 확인
            long downloadedBytes = 0;
            if (tempFile.exists()) {
                downloadedBytes = tempFile.length();
                Log.d(TAG, "이전에 다운로드된 파일 발견 ▶ " + FileUtils.formatFileSize(downloadedBytes));
            }

            // 다운로드 진행 추적자 초기화
            progressTracker = new DownloadProgressTracker(
                    callback,
                    state.getTotalBytes() > 0 ? state.getTotalBytes() : 0,
                    downloadedBytes);

            // 다운로드 작업 초기화
            downloadTask = new DownloadTask(
                    connectionManager,
                    progressTracker,
                    tempFile,
                    downloadFile);

            // 다운로드 작업 실행
            executeDownload(state, downloadedBytes);
        } catch (Exception e) {
            Log.e(TAG, "다운로드 시작 중 예외 발생", e);
            callback.onDownloadFailed(e.getMessage());
        }
    }

    /**
     * 다운로드 작업 실행
     *
     * @param state 다운로드 상태 객체
     * @param downloadedBytes 이미 다운로드된 바이트 수
     */
    private void executeDownload(final DownloadState state, final long downloadedBytes) {
        Thread downloadThread = new Thread(() -> {
            boolean success = downloadTask.startDownload(DOWNLOAD_URL, downloadedBytes, state);

            if (success) {
                stateManager.clearState();

                // 소요 시간 계산
                long downloadEndTime = System.currentTimeMillis();
                long downloadDuration = downloadEndTime - downloadStartTime;

                progressTracker.reportComplete(downloadDuration, downloadFile.length());

                Log.d(TAG, "다운로드 소요 시간 ▶ " + FileUtils.formatDownloadTime(downloadDuration));
            } else if (downloadTask.isDownloading()) {
                // 다운로드 상태 저장 (다시 시도가 가능하도록)
                saveDownloadState(state);
            }
        });

        downloadThread.start();
    }

    /**
     * 다운로드 상태 저장
     */
    private void saveDownloadState(DownloadState state) {
        if (tempFile.exists()) {
            long currentSize = tempFile.length();

            if (state.getTotalBytes() > 0) {
                state.setDownloadedBytes(currentSize);
                stateManager.saveState(state);

                int progress = (int) (currentSize * 100 / state.getTotalBytes());
                String message = String.format("다운로드 일시 중단 ▶ %d%% (%s / %s)",
                        progress,
                        FileUtils.formatFileSize(currentSize),
                        FileUtils.formatFileSize(state.getTotalBytes()));

                callback.onDownloadFailed(message);
            }
        }
    }

    /**
     * 앱 종료 시 다운로드 상태 저장
     */
    public void saveDownloadState() {
        if (tempFile.exists() && isDownloading()) {
            DownloadState currentState = stateManager.loadState();
            if (currentState != null && currentState.getTotalBytes() > 0) {
                currentState.setDownloadedBytes(tempFile.length());
                stateManager.saveState(currentState);
                Log.d(TAG, "앱 종료 시 다운로드 상태 저장 ▶ " + currentState.getDownloadedBytes() +
                        "/" + currentState.getTotalBytes());
            }
        }
    }

    /**
     * 다운로드 취소
     */
    public void cancelDownload() {
        if (isDownloading()) {
            if (downloadTask != null) {
                downloadTask.cancelDownload();
            }
        }
    }

    /**
     * 다운로드 상태 확인
     *
     * @return 다운로드 중이면 true, 아니면 false
     */
    public boolean isDownloading() {
        return downloadTask != null && downloadTask.isDownloading();
    }
}