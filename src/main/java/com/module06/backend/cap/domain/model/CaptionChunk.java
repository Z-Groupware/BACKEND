package com.module06.backend.cap.domain.model;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 참석자 브라우저가 회의 중에 보낸 자막 한 조각(caption_chunk, V5.2). CAP-11이 쓰고 CAP-12·13이 읽는다.
 * 정본(STT) 아님 — 실시간 표시 + STT 실패 폴백용. rms는 화자 판정(L1, capture 소유)의 유일한 근거라 필수다.
 *
 * 멱등 키는 (meetingId, memberId, seq) — 같은 조합 재전송은 DB UNIQUE 제약이 막고,
 * 서비스는 이를 에러로 보지 않고 조용히 건너뛴다(재전송에 안전한 실시간 스트림 특성).
 */
public class CaptionChunk {

    private final Long id;
    private final Long meetingId;
    private final Long memberId;
    private final int seq;
    private final int startOffsetMs;
    private final int endOffsetMs;
    private final String text;
    private final BigDecimal rms;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private CaptionChunk(Long id, Long meetingId, Long memberId, int seq, int startOffsetMs, int endOffsetMs,
                         String text, BigDecimal rms, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (meetingId == null || memberId == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_TEXT);
        }
        if (rms == null) {
            throw new BusinessException(CapErrorCode.CAP_RMS_REQUIRED);
        }
        this.id = id;
        this.meetingId = meetingId;
        this.memberId = memberId;
        this.seq = seq;
        this.startOffsetMs = startOffsetMs;
        this.endOffsetMs = endOffsetMs;
        this.text = text;
        this.rms = rms;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 수신 — CAP-11이 배치의 조각 하나를 기록할 때. memberId는 토큰에서 꺼낸 값만 들어온다(본문 아님).
    public static CaptionChunk receive(Long meetingId, Long memberId, int seq, int startOffsetMs, int endOffsetMs,
                                       String text, BigDecimal rms) {
        return new CaptionChunk(null, meetingId, memberId, seq, startOffsetMs, endOffsetMs, text, rms, null, null);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델)
    public static CaptionChunk restore(Long id, Long meetingId, Long memberId, int seq, int startOffsetMs,
                                       int endOffsetMs, String text, BigDecimal rms,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CaptionChunk(id, meetingId, memberId, seq, startOffsetMs, endOffsetMs, text, rms, createdAt, updatedAt);
    }

    public Long getId() { return id; }
    public Long getMeetingId() { return meetingId; }
    public Long getMemberId() { return memberId; }
    public int getSeq() { return seq; }
    public int getStartOffsetMs() { return startOffsetMs; }
    public int getEndOffsetMs() { return endOffsetMs; }
    public String getText() { return text; }
    public BigDecimal getRms() { return rms; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
