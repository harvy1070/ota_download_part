package com.example.otadown_rf.utils;

public class FileUtils {

    /**
     * B, KB, MB, GB
     *
     * @param size 바이트 단위의 파일 크기
     * @return 포맷된 파일 크기 문자열
     */
    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    /**
     * 밀리초 -> 시:분:초.밀리초
     *
     * @param millis 밀리초 단위의 시간
     * @return 포맷된 시간 문자열
     */
    public static String formatDownloadTime(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d시간 %d분 %d초", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds % 60);
        } else {
            return String.format("%.1f초", millis / 1000.0);
        }
    }
}
