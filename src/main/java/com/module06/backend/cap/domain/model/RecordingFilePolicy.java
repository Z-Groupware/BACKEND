package com.module06.backend.cap.domain.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

/* 완성 녹음 파일을 S3에 직접 업로드할 때 공통으로 적용하는 형식·용량 정책이다. */
public final class RecordingFilePolicy {

    /* 단일 S3 PUT 요청이 허용하는 최대 크기인 5GiB를 서버 계약으로 고정한다. */
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024 * 1024;

    /* 지원 확장자별 허용 MIME 타입이다. 브라우저의 범용 바이너리 타입도 함께 허용한다. */
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "wav", Set.of("audio/wav", "audio/x-wav", "audio/vnd.wave", "application/octet-stream"),
            "mp3", Set.of("audio/mpeg", "audio/mp3", "application/octet-stream"),
            "mp4", Set.of("audio/mp4", "video/mp4", "application/mp4", "application/octet-stream"),
            "m4a", Set.of("audio/mp4", "audio/x-m4a", "application/octet-stream"),
            "flac", Set.of("audio/flac", "audio/x-flac", "application/octet-stream"),
            "ogg", Set.of("audio/ogg", "application/ogg", "application/octet-stream"),
            "webm", Set.of("audio/webm", "video/webm", "application/octet-stream"),
            "amr", Set.of("audio/amr", "audio/amr-wb", "application/octet-stream")
    );

    private RecordingFilePolicy() {
    }

    /* 파일명, MIME 타입, 크기를 모두 검증하고 안전한 원본 파일명을 반환한다. */
    public static String validate(String fileName, String contentType, Long sizeBytes) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..")
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }
        if (sizeBytes == null || sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_SIZE_INVALID);
        }

        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 1 || extensionSeparator == fileName.length() - 1) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_FORMAT_UNSUPPORTED);
        }
        String extension = fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        Set<String> contentTypes = ALLOWED_CONTENT_TYPES.get(extension);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (contentTypes == null || !contentTypes.contains(normalizedContentType)) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_FORMAT_UNSUPPORTED);
        }
        return fileName;
    }

    public static String sanitizeForStorageName(String fileName) {
        StringBuilder sanitized = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char ch = fileName.charAt(i);
            sanitized.append(isStorageSafe(ch) ? ch : '_');
        }
        return sanitized.toString();
    }

    private static boolean isStorageSafe(char ch) {
        return (ch >= 'a' && ch <= 'z')
                || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9')
                || ch == '.'
                || ch == '-'
                || ch == '_';
    }
}
