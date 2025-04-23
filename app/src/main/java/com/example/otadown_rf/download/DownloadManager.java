package com.example.otadown_rf.download;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.otadown_rf.callback.DownloadCallback;
import com.example.otadown_rf.model.DownloadState;
import com.example.otadown_rf.model.DownloadStateManager;
import com.example.otadown_rf.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class DownloadManager {
    private static final String TAG = DownloadManager.class.getSimpleName();
    private static final String DOWNLOAD_URL = "https://s3.ap-southeast-2.amazonaws.com/avn.directed.kr/firmware/TEST/random_file_1GB.bin";

    private final Context context;
    private final File downloadDir;
    private final DownloadCallback callback;
    private final DownloadStateManager stateManager;

    private File downloadFile;
    private File tempFile;
    private boolean isDownloading = false;
    private long downloadStartTime;

    // 다운로드 상태 관리
    public DownloadManager(Context context, File downloadDir, DownloadCallback callback) {
        this.context = context;
        this.downloadDir = downloadDir;
        this.callback = callback;

        // 파일 경로 및 이름 설정
        downloadFile = new File(downloadDir, "update.bin");
        tempFile = new File(downloadDir, "update.bin.tmp");

        // 다운로드 상태 관리자 초기화
        stateManager = new DownloadStateManager(tempFile);
    }

    // 다운로드 체크
    public boolean isDownloading() {
        return isDownloading;
    }

    // 이어받기
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

    // 다운로드 진행
    public void startDownload() {
        if (isDownloading) {
            return;
        }

        isDownloading = true;
        downloadStartTime = System.currentTimeMillis();

        callback.onDownloadStarted("다운로드 준비 중...");

        try {
            downloadWithResume();
        } catch (Exception e) {
            Log.e(TAG, "다운로드 중 예외 상황 발생", e);
            isDownloading = false;
            callback.onDownloadFailed(e.getMessage());
        }
    }

    // 다운로드 취소
    public void cancelDownload() {
        if (isDownloading) {
            isDownloading = false;
            callback.onDownloadCancelled("다운로드 취소됨");
        }
    }

    public void saveDownloadState() {
        if (tempFile.exists() && isDownloading) {
            DownloadState currentState = stateManager.loadState();
            if (currentState != null && currentState.getTotalBytes() > 0) {
                currentState.setDownloadedBytes(tempFile.length());
                stateManager.saveState(currentState);
                Log.d(TAG, "앱 종료 시 다운로드 상태 저장 ▶ " + currentState.getDownloadedBytes() + "/" + currentState.getTotalBytes());
            }
        }
    }

    private void downloadWithResume() {
        // HTTP 로깅 인터셉터 설정
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.v(TAG, "OKHTTP ▶ " + message));
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        // OKHTTP 클라이언트 설정
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(logging).build();

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

        final long finalDownloadedBytes = downloadedBytes;

        // 요청 빌더
        Request.Builder requestBuilder = new Request.Builder().url(DOWNLOAD_URL);

        // range 헤더 추가 (이어받기)
        if (downloadedBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=" + downloadedBytes + "-");
            Log.d(TAG, "이어받기 요청 ▶ " + downloadedBytes + "바이트부터");
            callback.onProgressUpdate(0, "이어받기 준비 중... (" + FileUtils.formatFileSize(finalDownloadedBytes) + "부터)");
        }

        Request request = requestBuilder.build();
        Log.d(TAG, "HTTPS 요청 시작 ▶ " + DOWNLOAD_URL);

        try {
            // 사용자가 취소했는지 확인
            if (!isDownloading) {
                return;
            }

            // 요청 실행
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                isDownloading = false;
                callback.onDownloadFailed("서버 오류 ▶ " + response.code());
                return;
            }

            // HTTPS 연결 정보 로깅
            String protocol = response.protocol().toString();
            String cipher = response.handshake() != null ? response.handshake().cipherSuite().toString() : "알 수 없음";

            Log.d(TAG, "HTTPS 연결 성공");
            Log.d(TAG, "프로토콜 ▶ " + protocol);
            Log.d(TAG, "암호화 스위트 ▶ " + cipher);

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                isDownloading = false;
                callback.onDownloadFailed("응답 데이터가 없음");
                return;
            }

            // 전체 파일 크기 확인
            long totalBytes = getTotalBytes(response, responseBody, downloadedBytes);

            // 다운로드 정보 저장
            state.setTotalBytes(totalBytes);
            state.setDownloadedBytes(downloadedBytes);
            stateManager.saveState(state);

            BufferedSource source = responseBody.source();

            if (finalDownloadedBytes > 0) {
                callback.onProgressUpdate(
                        (int) (finalDownloadedBytes * 100 / totalBytes),
                        "이어받기 시작 (" + FileUtils.formatFileSize(finalDownloadedBytes) + "/" +
                                FileUtils.formatFileSize(totalBytes) + ")"
                );
            } else {
                callback.onProgressUpdate(0, "다운로드 시작 (총 " + FileUtils.formatFileSize(totalBytes) + ")");
            }

            // 다운로드 시작 로그
            Log.d(TAG, "다운로드 시작... 총 파일 크기 ▶ " + FileUtils.formatFileSize(totalBytes) +
                    ", 기존 다운로드 ▶ " + FileUtils.formatFileSize(downloadedBytes));

            // 파일 저장 시작
            if (!saveResponseToFile(responseBody, source, totalBytes, downloadedBytes, state)) {
                return;
            }

            // 완료 처리
            finalizeDownload();
        } catch (IOException e) {
            handleDownloadError(e);
        }
    }

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

    private boolean saveResponseToFile(ResponseBody responseBody, BufferedSource source, long totalBytes, long downloadedBytes, DownloadState state) throws IOException {
        BufferedSink sink = null;
        try {
            // 이어 쓰기 모드로 파일을 엶
            sink = Okio.buffer(Okio.appendingSink(tempFile));

            // 버퍼 설정
            Buffer buffer = new Buffer();
            long bytesReadThisSession = 0;
            long bytesReported = downloadedBytes;
            int bufferSize = 8 * 1024; // 8kb

            // 스트리밍 방식으로 다운로드 진행
            while (isDownloading) {
                long read = source.read(buffer, bufferSize);
                if (read == -1) break;

                sink.write(buffer, read);
                bytesReadThisSession += read;
                long totalBytesDownloaded = downloadedBytes + bytesReadThisSession;

                // 진행 상태 업데이트(5% 단위)
                if (totalBytes > 0) {
                    final int progress = (int) (totalBytesDownloaded * 100 / totalBytes);
                    long reportThreshold = totalBytes / 20; // 5%
                    final int roundedProgress = (progress / 5) * 5; // 5 배수로 반올림

                    if (totalBytesDownloaded - bytesReported >= reportThreshold) {
                        bytesReported = totalBytesDownloaded;

                        Log.v(TAG, String.format("다운로드 진행 ▶ %d%% (%s / %s)", roundedProgress,
                                FileUtils.formatFileSize(totalBytesDownloaded), FileUtils.formatFileSize(totalBytes)));
                        callback.onProgressUpdate(roundedProgress, String.format("다운로드 진행 중 %d%% (%s / %s)",
                                roundedProgress,
                                FileUtils.formatFileSize(totalBytesDownloaded),
                                FileUtils.formatFileSize(totalBytes)));
                    }
                }
            }

            // 다운로드 취소 확인
            if (!isDownloading) {
                Log.d(TAG, "다운로드 취소됨");
                callback.onDownloadCancelled("다운로드 취소됨");
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

    private void finalizeDownload() throws IOException {
        // 임시 파일을 실제 파일로 이동
        if (downloadFile.exists()) {
            downloadFile.delete();
        }

        if (!tempFile.renameTo(downloadFile)) {
            throw new IOException("파일 이름 변경 실패");
        }

        // 정보 초기화
        stateManager.clearState();

        // 소요 시간 계산
        long downloadEndTime = System.currentTimeMillis();
        long downloadDuration = downloadEndTime - downloadStartTime;
        String formattedTime = FileUtils.formatDownloadTime(downloadDuration);

        Log.d(TAG, "다운로드 완료, 파일 저장 위치 ▶ " + downloadFile.getAbsolutePath());
        Log.d(TAG, "파일 크기 ▶ " + FileUtils.formatFileSize(downloadFile.length()));
        Log.d(TAG, "다운로드 소요 시간 ▶ " + formattedTime);

        // 상태 업데이트
        isDownloading = false;
        callback.onDownloadComplete("다운로드 완료 ▶ " + FileUtils.formatFileSize(downloadFile.length()) +
                " (소요 시간 ▶ " + formattedTime + ")");
    }

    private void handleDownloadError(IOException e) {
        Log.e(TAG, "다운로드 중 오류 발생 ▶ ", e);

        // 다운로드 상태 저장 (다시 시도가 가능할 수 있도록)
        if (isDownloading && tempFile.exists()) {
            long currentSize = tempFile.length();
            DownloadState state = stateManager.loadState();

            if (state != null && state.getTotalBytes() > 0) {
                state.setDownloadedBytes(currentSize);
                stateManager.saveState(state);

                int progress = (int) (currentSize % 100 / state.getTotalBytes());
                String message = String.format("다운로드 일시 중단 ▶ %d%% (%s / %s)",
                        progress, FileUtils.formatFileSize(currentSize), FileUtils.formatFileSize(state.getTotalBytes()));

                isDownloading = false;
                callback.onDownloadFailed(message);
                return;
            }
        }

        isDownloading = false;
        callback.onDownloadFailed(e.getMessage());
    }
}
