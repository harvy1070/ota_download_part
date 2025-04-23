package com.example.otadown_rf.model;

import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

// SharedPreferences의 파일 기반 상태 관리 기능
public class DownloadStateManager {
    private static final String TAG = DownloadStateManager.class.getSimpleName();
    private final File tempFile;
    private final File stateFile;

    // @param tempFile 임시 파일의 경로
    public DownloadStateManager(File tempFile) {
        this.tempFile = tempFile;
        this.stateFile = new File(tempFile.getParentFile(), "download_state.dat");
    }

    // @param state 저장할 다운로드 상태 객체
    public void saveState(DownloadState state) {
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;

        try {
            SerializableDownloadState serializableState = new SerializableDownloadState(
                    state.getDownloadId(),
                    state.getDownloadedBytes(),
                    state.getTotalBytes()
            );

            fos = new FileOutputStream(stateFile);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(serializableState);
        } catch (IOException e) {
            Log.e(TAG, "다운로드 상태 저장 중 오류 발생", e);
        } finally {
            try {
                if (oos != null) oos.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                Log.e(TAG, "리소스 정리 중 오류 발생", e);
            }
        }
    }

    // @return 로드된 다운로드 상태 객체, 로드에 실패하면 null을 반환함
    public DownloadState loadState() {
        // 임시 파일이 없다면 상태 정보도 의미가 없어짐
        if (!tempFile.exists()) {
            return null;
        }

        // 상태 파일이 없으면 null 반환
        if (!stateFile.exists()) {
            return null;
        }

        FileInputStream fis = null;
        ObjectInputStream ois = null;

        try {
            fis = new FileInputStream(stateFile);
            ois = new ObjectInputStream(fis);

            SerializableDownloadState serializableState = (SerializableDownloadState) ois.readObject();
            DownloadState state = new DownloadState();
            state.setDownloadId(serializableState.downloadId);
            state.setDownloadedBytes(serializableState.downloadedBytes);
            state.setTotalBytes(serializableState.totalBytes);

            // 임시 파일 크기와 저장된 파일 크기가 다르면 파일 손상으로 간주함
            if (tempFile.length() != state.getDownloadedBytes()) {
                Log.w(TAG, "임시 파일 크기가 불일치함 ▶ " + tempFile.length() +
                        ", 저장된 크기 ▶ " + state.getDownloadedBytes());
                return null;
            }
            return state;
        } catch (Exception e) {
            Log.e(TAG, "다운로드 상태 로드 중 오류 발생", e);
            return null;
        } finally {
            try {
                if (ois != null) ois.close();
                if (fis != null) fis.close();
            } catch (IOException e) {
                Log.e(TAG, "리소스 정리 중 오류 발생", e);
            }
        }
    }

    // 저장된 다운로드 상태를 삭제함
    public void clearState() {
        if (stateFile.exists()) {
            stateFile.delete();
        }
    }

    // 직렬화를 위한 내부 클래스 설정
    private static class SerializableDownloadState implements Serializable {
        private static final long serialVersionUID = 1L;

        String downloadId;
        long downloadedBytes;
        long totalBytes;

        SerializableDownloadState(String downloadId, long downloadedBytes, long totalBytes) {
            this.downloadId = downloadId;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
        }
    }
}
