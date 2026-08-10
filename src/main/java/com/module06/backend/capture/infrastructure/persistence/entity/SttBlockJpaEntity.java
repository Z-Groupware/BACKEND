package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttCutReason;

/*
 * stt_block(V5.4) 매핑이다.
 *
 * <h2>⚠ 엔티티 이름을 반드시 다르게 준다</h2>
 * 같은 테이블을 cap 도 매핑한다({@code CapSttBlockReferenceEntity}, 읽기 전용). **엔티티명이
 * 겹치면 컨텍스트가 뜨지 않는다** — 그쪽 주석에 적힌 그대로다. 그래서 이쪽은 클래스명이
 * 다르고(SttBlock…), 이름이 같아질 여지를 없애려고 {@code @Entity(name)} 도 명시한다.
 *
 * 역할도 갈린다 — cap 은 삭제 판정에 status 만 읽고(@Immutable), **쓰기와 스키마 진화는 이쪽
 * 레인의 몫**이다.
 *
 * <h2>status·cut_reason 을 enum 으로 매핑한다</h2>
 * cap 쪽은 String 으로 읽는다(그쪽이 capture 도메인에 의존하지 않으려고). 여기서는 값의 뜻이
 * 곧 판정이라 enum 이 맞다 — 재처리 대상이 FAILED 뿐이라는 규칙을 문자열 비교로 두면 오타가
 * 컴파일을 지나간다.
 */
@Entity(name = "CaptureSttBlock")
@Table(name = "stt_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttBlockJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 회의 내 순번(0부터). 화면과 STT-04 의 path variable 이 이 값이다. */
    @Column(name = "block_seq", nullable = false)
    private int blockSeq;

    @Column(name = "start_offset_ms", nullable = false)
    private int startOffsetMs;

    @Column(name = "end_offset_ms", nullable = false)
    private int endOffsetMs;

    /* FALLBACK_OVERLAP 인 블록은 경계에서 발화가 잘렸을 수 있다 — 품질 조사의 출발점이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cut_reason", nullable = false)
    private SttCutReason cutReason;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    /* 계정 내 유일해야 한다. retryCount 가 들어 있다(meeting-500-block-3-r3). */
    @Column(name = "provider_job_name", length = 200)
    private String providerJobName;

    /* 블록 오디오. 두 EC2 사이 파일 전달은 S3 경유만이라, 없으면 재제출할 대상이 없다. */
    @Column(name = "audio_s3_key", length = 1024)
    private String audioS3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SttBlockStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /* JOB_FAILED 등. 사용자에게 그대로 노출하지 않는다 — 화면은 이 코드로 문구를 고른다. */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /*
     * 새 블록을 QUEUED로 만든다(10분/40청크 자동 트리거 전용). retry()의 markQueuedForRetry와
     * 달리 이전 실패 흔적을 지울 필요가 없다 — 처음 만드는 행이라 지울 과거 자체가 없다.
     */
    public static SttBlockJpaEntity createQueued(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs,
                                                 SttCutReason cutReason, String audioS3Key, String provider,
                                                 String providerJobName) {
        SttBlockJpaEntity entity = new SttBlockJpaEntity();
        entity.meetingId = meetingId;
        entity.blockSeq = blockSeq;
        entity.startOffsetMs = startOffsetMs;
        entity.endOffsetMs = endOffsetMs;
        entity.cutReason = cutReason;
        entity.audioS3Key = audioS3Key;
        entity.provider = provider;
        entity.providerJobName = providerJobName;
        entity.status = SttBlockStatus.QUEUED;
        entity.retryCount = 0;
        return entity;
    }

    /*
     * 재처리를 접수한다(STT-04).
     *
     * 이전 실패의 흔적을 지운다 — errorCode 와 finishedAt 이 남아 있으면 이번 시도가 아직
     * 돌고 있는데도 화면이 **지난 실패 사유를 이번 것으로 보여준다.** 반대로 retryCount 는
     * 누적한다: 몇 번이나 실패했는지가 곧 이 블록을 포기할지 판단하는 근거다.
     *
     * @return 올라간 뒤의 시도 횟수. 잡 이름에 이미 들어간 값과 같아야 한다
     */
    public int markQueuedForRetry(String provider, String providerJobName) {
        this.status = SttBlockStatus.QUEUED;
        this.retryCount += 1;
        this.provider = provider;
        this.providerJobName = providerJobName;
        this.errorCode = null;
        this.startedAt = null;
        this.finishedAt = null;
        return this.retryCount;
    }
}
