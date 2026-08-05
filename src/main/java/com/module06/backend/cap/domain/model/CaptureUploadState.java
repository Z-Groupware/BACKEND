package com.module06.backend.cap.domain.model;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * 회의당 1행 — CAP 자체 세그먼트/현재 녹음자 북키핑. D(회의) 도메인의 미래 capture_session과는
 * 별개 개념: 저 테이블은 아직 없고(D 미구현), "현재 녹음자가 누구인가"는 CAP이 직접 소유해야
 * presign/complete가 D를 기다리지 않고 지금 바로 동작한다.
 */
public class CaptureUploadState {

    private final Long meetingId;
    private int segmentSeq;
    private Long recorderPersonId;
    private int lastSeq;
    private int blocksFormed;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private CaptureUploadState(Long meetingId, int segmentSeq, Long recorderPersonId, int lastSeq,
                               int blocksFormed, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (meetingId == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }
        this.meetingId = meetingId;
        this.segmentSeq = segmentSeq;
        this.recorderPersonId = recorderPersonId;
        this.lastSeq = lastSeq;
        this.blocksFormed = blocksFormed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 생성 — 이 회의에 처음 presign을 호출한 사람을 녹음자로 삼아 세그먼트 0부터 시작
    public static CaptureUploadState startWithRecorder(Long meetingId, Long recorderPersonId) {
        return new CaptureUploadState(meetingId, 0, recorderPersonId, 0, 0, null, null);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델)
    public static CaptureUploadState restore(Long meetingId, int segmentSeq, Long recorderPersonId, int lastSeq,
                                             int blocksFormed, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CaptureUploadState(meetingId, segmentSeq, recorderPersonId, lastSeq, blocksFormed,
                createdAt, updatedAt);
    }

    /**
     * 녹음자 배정/검증. 아직 아무도 녹음 중이 아니면 호출자가 녹음자가 되고, 이미 녹음자가 있으면
     * 본인 확인 — 다른 사람이면 canTakeover(하트비트 30초 초과, CaptureHeartbeatPort 판정)일 때만
     * 교체를 허용한다.
     */
    public void assignOrVerifyRecorder(Long callerId, boolean canTakeover) {
        if (recorderPersonId == null) {
            recorderPersonId = callerId;
            return;
        }
        if (recorderPersonId.equals(callerId)) {
            return;
        }
        if (!canTakeover) {
            throw new BusinessException(CapErrorCode.CAP_NOT_CURRENT_RECORDER);
        }
        // 이어받기 — 녹음자 교체 + 새 MediaRecorder 세션이므로 세그먼트 번호를 올린다.
        recorderPersonId = callerId;
        segmentSeq++;
    }

    /** 현재 녹음자인지 검증만 한다(상태 변경 없음). 아니면 CAP_NOT_CURRENT_RECORDER — 상태 조회(#4)처럼 읽기 권한 확인용. */
    public void verifyRecorder(Long callerId) {
        if (recorderPersonId == null || !recorderPersonId.equals(callerId)) {
            throw new BusinessException(CapErrorCode.CAP_NOT_CURRENT_RECORDER);
        }
    }

    /** 청크 업로드 완료 통보 반영 — 현재 녹음자만 가능, lastSeq는 단조 증가만 허용. */
    public void recordUpload(Long callerId, int seq) {
        verifyRecorder(callerId);
        if (seq > lastSeq) {
            lastSeq = seq;
        }
    }

    // STT 블록이 하나 더 조립됐을 때 카운트 증가 (이번 PR에선 아직 호출되지 않음 — STT 도메인 배선 후 사용)
    public void incrementBlocksFormed() {
        blocksFormed++;
    }

    public Long getMeetingId() { return meetingId; }
    public int getSegmentSeq() { return segmentSeq; }
    public Long getRecorderPersonId() { return recorderPersonId; }
    public int getLastSeq() { return lastSeq; }
    public int getBlocksFormed() { return blocksFormed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
