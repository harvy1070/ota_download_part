package com.example.otadown_rf.network;

import android.util.Log;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class ConnectionManager {
    private static final String TAG = ConnectionManager.class.getSimpleName();
    private final OkHttpClient client;

    public ConnectionManager() {
        // HTTP 로깅 인터셉터 설정
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.v(TAG, "OKHTTP ▶ " + message));
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        // OKHTTP 클라이언트 생성
        client = new OkHttpClient.Builder().addInterceptor(logging).build();
    }

    /**
     * 서버에 연결해서 응답을 받아오는 구간
     * @param url 연결할 url
     * @param rangeStart 이어받기를 위한 시작 위치(0이면 처음부터)
     * @return 서버 응답
     * @throws IOException 연결 오류 발생 시
     */
    public Response connect(String url, long rangeStart) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);

        // range 헤더 추가
        if (rangeStart > 0) {
            requestBuilder.addHeader("Range", "bytes=" + rangeStart + "-");
            Log.d(TAG, "이어받기 요청 ▶ " + rangeStart + " 바이트부터");
        }

        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * HEAD 요청을 보내 파일 크기등의 정보 확인하는 구간
     *
     * @param url 확인할 url
     * @return 서버 응답
     * @throws IOException 연결 오류 발생 시
     */
    public Response checkFileInfo(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();
        return client.newCall(request).execute();
    }

    /**
     * 서버 상태 체크
     * @param url 체크할 url
     * @return 서버가 정상이면 ture 반환, 아닐 시 false 반환
     */
    public boolean isServerAvailable(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head() // HEAD 요청
                    .build();

            Response response = client.newCall(request).execute();
            boolean isSuccess = response.isSuccessful();
            response.close();

            return isSuccess;
        } catch (IOException e) {
            Log.e(TAG, "서버 연결 확인 중 오류", e);
            return false;
        }
    }
}
