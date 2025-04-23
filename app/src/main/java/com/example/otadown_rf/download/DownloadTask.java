package com.example.otadown_rf.download;

import android.util.Log;

import com.example.otadown_rf.model.DownloadState;
import com.example.otadown_rf.network.ConnectionManager;

import java.io.File;
import java.io.IOException;

import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * 실제 다운로드 작업을 처리하는 클래스
 */
public class DownloadTask {
    private static final String TAG = DownloadTask.class.getSimpleName();

    private final ConnectionManager connectionManager;
    private final DownloadProgressTracker progressTracker;
    private final File tempFile;
    private final File downloadFile;

    private boolean isDownloading = false;

    /**
     * DownloadTask 생성자
     *
     * @param connectionManager 네트워크 연결 관리자
     * @param progressTracker 다운로드 진행 상황 추적자
     * @param tempFile 임시 저장 파일
     * @param downloadFile 최종 다운로드 파일
     */
    public DownloadTask(ConnectionManager connectionManager,
                        DownloadProgressTracker progressTracker,
                        File tempFile,
                        File downloadFile) {
        this.connectionManager = connectionManager;
        this.progressTracker = progressTracker;
        this.tempFile = tempFile;
        this.downloadFile = downloadFile;
    }

    /**
     * 다운로드 작업 시작
     *
     * @param url 다운로드할 파일의 URL
     * @param downloadedBytes 이미 다운로드된 바이트 수
     * @param state 다운로드 상태 객체
     * @return 다운로드 성공 여부
     */
    public boolean startDownload(String url, long downloadedBytes, DownloadState state) {
        isDownloading = true;

        try {
            // 서버 가용성 확인
            if (!connectionManager.isServerAvailable(url)) {
                progressTracker.reportFailure("서버에 연결할 수 없습니다");
                return false;
            }

            // 서버에 연결
            Response response = connectionManager.connect(url, downloadedBytes);

            if (!response.isSuccessful()) {
                progressTracker.reportFailure("서버 오류 ▶ " + response.code());
                return false;
            }

            // HTTPS 연결 정보 로깅
            logConnectionInfo(response);

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                progressTracker.reportFailure("응답 데이터가 없음");
                return false;
            }

            // 전체 파일 크기 확인
            long totalBytes = getTotalBytes(response, responseBody, downloadedBytes);

            // 상태 업데이트
            state.setTotalBytes(totalBytes);
            state.setDownloadedBytes(downloadedBytes);

            // 다운로드 시작 보고
            progressTracker.reportStart();

            // 다운로드 시작 로그
            Log.d(TAG, "다운로드 시작... 총 파일 크기 ▶ " + totalBytes +
                    ", 기존 다운로드 ▶ " + downloadedBytes);

            // 파일 다운로드 및 저장
            if (!downloadFile(responseBody, totalBytes, downloadedBytes)) {
                return false;
            }

            // 다운로드 파일 이름 변경 (임시 -> 최종)
            finalizeDownload();

            return true;
        } catch (IOException e) {
            Log.e(TAG, "다운로드 중 오류 발생", e);
            progressTracker.reportFailure(e.getMessage());
            return false;
        } finally {
            isDownloading = false;
        }
    }

    /**
     * 다운로드 취소
     */
    public void cancelDownload() {
        isDownloading = false;
    }

    /**
     * 현재 다운로드 상태
     *
     * @return 다운로드 중이면 true, 아니면 false
     */
    public boolean isDownloading() {
        return isDownloading;
    }

    /**
     * 연결 정보 로깅
     */
    private void logConnectionInfo(Response response) {
        String protocol = response.protocol().toString();
        String cipher = response.handshake() != null ?
                response.handshake().cipherSuite().toString() : "알 수 없음";

        Log.d(TAG, "HTTPS 연결 성공");
        Log.d(TAG, "프로토콜 ▶ " + protocol);
        Log.d(TAG, "암호화 스위트 ▶ " + cipher);
    }

    /**
     * 전체 파일 크기 확인
     */
    private long getTotalBytes(Response response, ResponseBody responseBody, long downloadBytes) {
        long totalBytes;
        if (response.code() == 206) {
            String contentRange = response.header("Content-Range");
            if (contentRange != null && contentRange.startsWith("bytes ")) {
                String[] parts = contentRange.substring(6).split("/");
                if (parts.length == 2) {
                    totalBytes = Long.parseLong(parts[1]);
                } else {
                    totalBytes = downloadBytes + responseBody.contentLength();
                }
            } else {
                totalBytes = downloadBytes + responseBody.contentLength();
            }
        } else {
            totalBytes = responseBody.contentLength();
            // 새 다운로드인 경우 이전 임시 파일 삭제함
            if (tempFile.exists()) {
                tempFile.delete();
                downloadBytes = 0;
            }
        }
        return totalBytes;
    }

    /**
     * 파일 다운로드 및 저장
     */
    private boolean downloadFile(ResponseBody responseBody, long totalBytes, long downloadedBytes)
            throws IOException {
        BufferedSink sink = null;
        try {
            // 이어 쓰기 모드로 파일을 엶
            sink = Okio.buffer(Okio.appendingSink(tempFile));

            // 버퍼 설정
            Buffer buffer = new Buffer();
            long bytesReadThisSession = 0;
            int bufferSize = 8 * 1024; // 8kb

            // 스트리밍 방식으로 다운로드 진행
            BufferedSource source = responseBody.source();
            while (isDownloading) {
                long read = source.read(buffer, bufferSize);
                if (read == -1) break;

                sink.write(buffer, read);
                bytesReadThisSession += read;
                long totalBytesDownloaded = downloadedBytes + bytesReadThisSession;

                // 진행 상황 업데이트
                progressTracker.updateProgress(totalBytesDownloaded);
            }

            // 다운로드 취소 확인
            if (!isDownloading) {
                Log.d(TAG, "다운로드 취소됨");
                progressTracker.reportCancellation("다운로드 취소됨");
                return false;
            }

            sink.flush();
            return true;
        } finally {
            if (sink != null) {
                try {
                    sink.close();
                } catch (IOException e) {
                    Log.e(TAG, "리소스 정리 오류", e);
                }
            }
            responseBody.close();
        }
    }

    /**
     * 다운로드 완료 후 파일 이름 변경
     */
    private void finalizeDownload() throws IOException {
        // 임시 파일을 실제 파일로 이동
        if (downloadFile.exists()) {
            downloadFile.delete();
        }

        if (!tempFile.renameTo(downloadFile)) {
            throw new IOException("파일 이름 변경 실패");
        }

        Log.d(TAG, "다운로드 완료, 파일 저장 위치 ▶ " + downloadFile.getAbsolutePath());
        Log.d(TAG, "파일 크기 ▶ " + downloadFile.length());
    }
}