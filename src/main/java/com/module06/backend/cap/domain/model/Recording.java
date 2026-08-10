package com.module06.backend.cap.domain.model;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * 회의 통합 녹음본 메타데이터(recording 테이블, V1). CAP-05 조립본 또는 CAP-10 수동 업로드본 한 건을 나타낸다.
 * 실제 오디오는 스토리지(file_url=s3Key)에 있고, 이 모델은 파일명·용량·길이만 담는다.
 *
 * durationSec는 등록 시점엔 null일 수 있다 — 수동 업로드(CAP-10)는 ffprobe/조립 파이프라인이 길이를
 * 나중에 async로 채우기 때문이다(등록은 즉시, 길이 복구는 downstream).
 */
public class Recording {

    private final Long id;
    private final Long meetingId;
    private final String fileName;
    private final String fileUrl;
    private final long sizeBytes;
    private final Integer durationSec;
    private final boolean sttTriggered;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Recording(Long id, Long meetingId, String fileName, String fileUrl, long sizeBytes,
                      Integer durationSec, boolean sttTriggered, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (meetingId == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }
        if (isBlank(fileName) || isBlank(fileUrl)) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_TEXT);
        }
        if (sizeBytes < 0) {
            throw new BusinessException(CapErrorCode.CAP_INVALID_PART_SIZE);
        }
        this.id = id;
        this.meetingId = meetingId;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.sizeBytes = sizeBytes;
        this.durationSec = durationSec;
        this.sttTriggered = sttTriggered;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 등록(STT 트리거 없음 — 기본값). 실제로 저장 직후 STT를 트리거하는 경로는 아래 5-인자 오버로드를 쓴다.
    public static Recording register(Long meetingId, String fileName, String fileUrl, long sizeBytes) {
        return register(meetingId, fileName, fileUrl, sizeBytes, false);
    }

    // 신규 등록 — 수동 업로드/조립 완료본을 recording 테이블에 처음 기록할 때. durationSec는 파이프라인이 나중에 채운다.
    // sttTriggered: 저장 직후 STT 트리거를 실제로 호출하는 경로면 true로 등록한다 — stt_block이 아직 0건이어도
    // "미시작"과 "완료"를 구분하기 위한 신호다(CAP-15 삭제 차단 판정용).
    public static Recording register(Long meetingId, String fileName, String fileUrl, long sizeBytes,
                                     boolean sttTriggered) {
        return new Recording(null, meetingId, fileName, fileUrl, sizeBytes, null, sttTriggered, null, null);
    }

    // 신규 등록 — 조립 파이프라인이 duration을 이미 계산해둔 경우(CAP-05 자동/수동 조립).
    // ManualRecordingService의 register(...)와 달리, 조립은 업로드 직후가 아니라 ffmpeg/ffprobe로
    // 파일 자체를 다 만든 뒤에 등록하므로 durationSec을 처음부터 채워 넣을 수 있다.
    public static Recording registerWithDuration(Long meetingId, String fileName, String fileUrl, long sizeBytes,
                                                 Integer durationSec, boolean sttTriggered) {
        return new Recording(null, meetingId, fileName, fileUrl, sizeBytes, durationSec, sttTriggered, null, null);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델). sttTriggered 없이 호출하는 기존 테스트 호환용.
    public static Recording restore(Long id, Long meetingId, String fileName, String fileUrl, long sizeBytes,
                                    Integer durationSec, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return restore(id, meetingId, fileName, fileUrl, sizeBytes, durationSec, false, createdAt, updatedAt);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델).
    public static Recording restore(Long id, Long meetingId, String fileName, String fileUrl, long sizeBytes,
                                    Integer durationSec, boolean sttTriggered,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Recording(id, meetingId, fileName, fileUrl, sizeBytes, durationSec, sttTriggered, createdAt, updatedAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Long getId() { return id; }
    public Long getMeetingId() { return meetingId; }
    public String getFileName() { return fileName; }
    public String getFileUrl() { return fileUrl; }
    public long getSizeBytes() { return sizeBytes; }
    public Integer getDurationSec() { return durationSec; }
    public boolean isSttTriggered() { return sttTriggered; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
