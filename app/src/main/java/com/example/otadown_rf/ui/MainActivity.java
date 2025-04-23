package com.example.otadown_rf.ui;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.otadown_rf.download.DownloadManager;
import com.example.otadown_rf.callback.DownloadCallback;
import com.example.otadown_rf.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final String TAG = MainActivity.class.getSimpleName();

    // UI 요소
    private TextView tvCurrentVersion;
    private TextView tvStatus;
    private Button btnDownload;
    private ProgressBar progressBar;

    // 버전 초기값
    private double currentVersion = 1.0;

    // 다운로드 매니저
    private com.example.otadown_rf.download.DownloadManager downloadManager;

    // 스레드 풀 - 백그라운드 작업 위한 ExecutorService 작업
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // UI 요소 초기화
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion);
        tvStatus = findViewById(R.id.tvStatus);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);

        // 버전 표시
        tvCurrentVersion.setText("현재 버전 ▶ " + String.format("%.1f", currentVersion));

        // 다운로드 버튼 이벤트 설정
        btnDownload.setOnClickListener(this);

        // ExecuteService 초기화 - 단일 스레드 풀
        executorService = Executors.newSingleThreadExecutor();

        // 다운로드 관리자
        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        downloadManager = new DownloadManager(this, downloadDir, new DownloadCallback() {
            // 다운로드 시작
            @Override
            public void onDownloadStarted(String message) {
                updateUI(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    tvStatus.setText(message);
                    btnDownload.setText("취소");
                });
            }

            // 다운로드 진행 상황 업데이트
            @Override
            public void onProgressUpdate(int progress, String message) {
                updateUI(() -> {
                    progressBar.setProgress(progress);
                    tvStatus.setText(message);
                });
            }

            // 다운로드 성공
            @Override
            public void onDownloadComplete(String message) {
                updateUI(() -> {
                    tvStatus.setText(message);
                    progressBar.setProgress(100);
                    btnDownload.setText("다운로드");

                    // 버전 업데이트(테스트용)
                    currentVersion += 0.1;
                    tvCurrentVersion.setText("현재 버전 ▶ " + String.format("%.1f", currentVersion));

                    Toast.makeText(MainActivity.this, "다운로드 완료", Toast.LENGTH_SHORT).show();
                });
            }

            // 다운로드 실패
            @Override
            public void onDownloadFailed(String message) {
                updateUI(() -> {
                    tvStatus.setText("다운로드 실패 ▶ " + message);
                    progressBar.setVisibility(View.INVISIBLE);
                    btnDownload.setText("다운로드");
                    Toast.makeText(MainActivity.this, "다운로드 실패 ▶ " + message, Toast.LENGTH_SHORT).show();
                });
            }

            // 다운로드 취소
            @Override
            public void onDownloadCancelled(String message) {
                updateUI(() -> {
                    tvStatus.setText(message);
                    btnDownload.setText("다운로드");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
        // 이전 다운로드 상태 확인
        downloadManager.checkPreviousDownload();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnDownload) {
            if (downloadManager.isDownloading()) {
                // 이미 다운로드 중이라면 취소함
                downloadManager.cancelDownload();
                Toast.makeText(this, "다운로드 취소 중...", Toast.LENGTH_SHORT).show();
            } else {
                // 다운로드 시작
                executorService.execute(() -> downloadManager.startDownload());
            }
        }
    }

    // UI 업데이트를 위한 헬퍼 메서드
    private void updateUI(Runnable action) {
        runOnUiThread(action);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 다운로드 상태 저장
        if (downloadManager != null && downloadManager.isDownloading()) {
            downloadManager.saveDownloadState();
        }

        // ExecutorService 종료
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}